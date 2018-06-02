package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraintObserver;
import org.thoughtcrime.securesms.logging.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

public class IncomingMessageObserver implements InjectableType, ConstraintObserver.Notifier {

  private static final String TAG = IncomingMessageObserver.class.getSimpleName();

  public  static final  int FOREGROUND_ID            = 313399;
  private static final long REQUEST_TIMEOUT_MINUTES  = 1;

  private static SignalServiceMessagePipe pipe             = null;
  private static SignalServiceMessagePipe unidentifiedPipe = null;

  private final Context           context;
  private final NetworkConstraint networkConstraint;

  public static AtomicReference<SignalServiceMessagePipe> pipeReference = new AtomicReference<>();
  public static AtomicReference<SignalServiceMessagePipe> unidentifiedPipeReference = new AtomicReference<>();

  private final MessageRetrievalThread retrievalThread;

  private BroadcastReceiver connectivityChangeReceiver;
  private ConnectivityManager.NetworkCallback connectivityChangeCallback;

  private boolean appVisible;

  @Inject SignalServiceMessageReceiver receiver;
  @Inject SignalServiceNetworkAccess   networkAccess;

  public IncomingMessageObserver(@NonNull Context context) {
    ApplicationContext.getInstance(context).injectDependencies(this);

    this.context            = context;
    this.networkConstraint = new NetworkConstraint.Factory(ApplicationContext.getInstance(context)).create();

    new NetworkConstraintObserver(ApplicationContext.getInstance(context)).register(this);

    retrievalThread = new MessageRetrievalThread();
    retrievalThread.start();

    if (TextSecurePreferences.isFcmDisabled(context)) {
      ContextCompat.startForegroundService(context, new Intent(context, ForegroundService.class));
      setupNetworkMonitoring();
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
  public void onConstraintMet(@NonNull String reason) {
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
    boolean isGcmDisabled = TextSecurePreferences.isFcmDisabled(context);

    Log.d(TAG, String.format("Network requirement: %s, app visible: %s, gcm disabled: %b",
                             networkConstraint.isMet(), appVisible, isGcmDisabled));

    return TextSecurePreferences.isPushRegistered(context)      &&
           TextSecurePreferences.isWebsocketRegistered(context) &&
           (appVisible || isGcmDisabled)                        &&
           networkConstraint.isMet()                       &&
           !networkAccess.isCensored(context);
  }

  private synchronized void waitForConnectionNecessary() {
    while (!isConnectionNecessary()) {
      try {
        wait();
      } catch (InterruptedException e) {
        Log.d(TAG, "Retrieval thread interrupted while not connected; ignoring.");
      }
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
          while (isConnectionNecessary() && !interrupted()) {
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
        } catch (InterruptedException e) {
          Log.d(TAG, "Retrieval thread interrupted.");
        } catch (IOException e) {
          Log.d(TAG, "Message pipe failed: " + e.getMessage());
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

  private void setupNetworkMonitoring() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

      connectivityChangeCallback = new ConnectivityManager.NetworkCallback() {
        private Network current;

        private void update(Network network) {
          final Network previous = current;
          current = network;

          Log.d(TAG, "Currently active network: " + network);

          if (previous != null && (current == null || !current.equals(previous))) {
            Log.d(TAG,
                  "Active network changed (" + previous + " -> " + current +
                  "); interrupting the retrieval thread to recycle the pipe.");

            retrievalThread.interrupt();
          }
        }

        @Override
        public void onAvailable(Network network) {
          final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

          update(connectivityManager.getActiveNetwork());
        }

        @Override
        public void onLost(Network network) {
          final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

          update(connectivityManager.getActiveNetwork());
        }
      };

      connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(),
                                                  connectivityChangeCallback);
    } else {
      connectivityChangeReceiver = new BroadcastReceiver() {
        private int current = -1;

        @Override
        public void onReceive(Context context, Intent intent) {
          final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

          final NetworkInfo info = connectivityManager.getActiveNetworkInfo();
          final int previous = current;

          if (info == null) {
            current = -1;
          } else if (info.isConnected()) {
            current = info.getType();
          }

          Log.d(TAG, "Currently active network: " + current);

          if (previous != -1 && previous != current) {
            Log.d(TAG,
                  "Active network changed (" + previous + " -> " + current +
                  "); interrupting the retrieval thread to recycle the pipe.");
            retrievalThread.interrupt();
          }
        }
      };

      context.registerReceiver(connectivityChangeReceiver,
                               new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
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
