package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirement;
import org.thoughtcrime.securesms.jobmanager.requirements.NetworkRequirementProvider;
import org.thoughtcrime.securesms.jobmanager.requirements.RequirementListener;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

public class IncomingMessageObserver implements InjectableType, RequirementListener {

  private static final String TAG = IncomingMessageObserver.class.getSimpleName();

  public  static final  int FOREGROUND_ID            = 313399;
  private static final long REQUEST_TIMEOUT_MINUTES  = 1;

  private static SignalServiceMessagePipe pipe             = null;
  private static SignalServiceMessagePipe unidentifiedPipe = null;

  private final Context            context;
  private final NetworkRequirement networkRequirement;

  private boolean appVisible;

  @Inject SignalServiceMessageReceiver receiver;
  @Inject SignalServiceNetworkAccess   networkAccess;

  public IncomingMessageObserver(@NonNull Context context) {
    ApplicationContext.getInstance(context).injectDependencies(this);

    this.context            = context;
    this.networkRequirement = new NetworkRequirement(context);

    new NetworkRequirementProvider(context).setListener(this);
    new MessageRetrievalThread().start();

    if (TextSecurePreferences.isGcmDisabled(context)) {
      ContextCompat.startForegroundService(context, new Intent(context, ForegroundService.class));
    }

    ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner) {
        onAppForegrounded();
      }

      @Override
      public void onStop(@NonNull LifecycleOwner owner) {
        onAppBackgrounded();
      }
    });
  }

  @Override
  public void onRequirementStatusChanged() {
    synchronized (this) {
      notifyAll();
    }
  }

  private synchronized void onAppForegrounded() {
    appVisible = true;
    notifyAll();
  }

  private synchronized void onAppBackgrounded() {
    appVisible = false;
    notifyAll();
  }

  private synchronized boolean isConnectionNecessary() {
    boolean isGcmDisabled = TextSecurePreferences.isGcmDisabled(context);

    Log.d(TAG, String.format("Network requirement: %s, app visible: %s, gcm disabled: %b",
                             networkRequirement.isPresent(), appVisible, isGcmDisabled));

    return TextSecurePreferences.isPushRegistered(context)      &&
           TextSecurePreferences.isWebsocketRegistered(context) &&
           (appVisible || isGcmDisabled)                        &&
           networkRequirement.isPresent()                       &&
           !networkAccess.isCensored(context);
  }

  private synchronized void waitForConnectionNecessary() {
    try {
      while (!isConnectionNecessary()) wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private void shutdown(SignalServiceMessagePipe pipe, SignalServiceMessagePipe unidentifiedPipe) {
    try {
      pipe.shutdown();
      unidentifiedPipe.shutdown();
    } catch (Throwable t) {
      Log.w(TAG, t);
    }
  }

  public static @Nullable SignalServiceMessagePipe getPipe() {
    return pipe;
  }

  public static @Nullable SignalServiceMessagePipe getUnidentifiedPipe() {
    return unidentifiedPipe;
  }

  private class MessageRetrievalThread extends Thread implements Thread.UncaughtExceptionHandler {

    MessageRetrievalThread() {
      super("MessageRetrievalService");
      setUncaughtExceptionHandler(this);
    }

    @Override
    public void run() {
      while (true) {
        Log.i(TAG, "Waiting for websocket state change....");
        waitForConnectionNecessary();

        Log.i(TAG, "Making websocket connection....");
        pipe             = receiver.createMessagePipe();
        unidentifiedPipe = receiver.createUnidentifiedMessagePipe();

        SignalServiceMessagePipe localPipe             = pipe;
        SignalServiceMessagePipe unidentifiedLocalPipe = unidentifiedPipe;

        try {
          while (isConnectionNecessary()) {
            try {
              Log.i(TAG, "Reading message...");
              localPipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                             envelope -> {
                               Log.i(TAG, "Retrieved envelope! " + String.valueOf(envelope.getSource()));
                               new PushContentReceiveJob(context).processEnvelope(envelope);
                             });
            } catch (TimeoutException e) {
              Log.w(TAG, "Application level read timeout...");
            } catch (InvalidVersionException e) {
              Log.w(TAG, e);
            }
          }
        } catch (Throwable e) {
          Log.w(TAG, e);
        } finally {
          Log.w(TAG, "Shutting down pipe...");
          shutdown(localPipe, unidentifiedLocalPipe);
        }

        Log.i(TAG, "Looping...");
      }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      Log.w(TAG, "*** Uncaught exception!");
      Log.w(TAG, e);
    }
  }

  public static class ForegroundService extends Service {

    @Override
    public @Nullable IBinder onBind(Intent intent) {
      return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      super.onStartCommand(intent, flags, startId);

      NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NotificationChannels.OTHER);
      builder.setContentTitle(getApplicationContext().getString(R.string.MessageRetrievalService_signal));
      builder.setContentText(getApplicationContext().getString(R.string.MessageRetrievalService_background_connection_enabled));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setWhen(0);
      builder.setSmallIcon(R.drawable.ic_signal_background_connection);
      startForeground(FOREGROUND_ID, builder.build());

      return Service.START_STICKY;
    }
  }
}
