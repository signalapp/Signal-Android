package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.api.TextSecureMessagePipe;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

public class MessageRetrievalService extends Service implements Runnable, InjectableType, RequirementListener {

  private static final String TAG = MessageRetrievalService.class.getSimpleName();

  public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
  public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";
  public static final  String ACTION_PUSH_RECEIVED     = "PUSH_RECEIVED";
  private static final long   REQUEST_TIMEOUT_MINUTES  = 2;

  private final ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory());

  private NetworkRequirement         networkRequirement;
  private NetworkRequirementProvider networkRequirementProvider;

  @Inject
  public TextSecureMessageReceiver receiver;

  private int     activeActivities = 0;
  private boolean pushPending      = false;

  @Override
  public void onCreate() {
    super.onCreate();
    ApplicationContext.getInstance(this).injectDependencies(this);

    networkRequirement         = new NetworkRequirement(this);
    networkRequirementProvider = new NetworkRequirementProvider(this);

    networkRequirementProvider.setListener(this);
    executor.submit(this);
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    if      (ACTION_ACTIVITY_STARTED.equals(intent.getAction()))  incrementActive();
    else if (ACTION_ACTIVITY_FINISHED.equals(intent.getAction())) decrementActive();
    else if (ACTION_PUSH_RECEIVED.equals(intent.getAction()))     incrementPushReceived();

    return START_STICKY;
  }

  @Override
  public void run() {
    while (true) {
      Log.w(TAG, "Waiting for websocket state change....");
      waitForConnectionNecessary();

      Log.w(TAG, "Making websocket connection....");
      TextSecureMessagePipe pipe = receiver.createMessagePipe();

      try {
        while (isConnectionNecessary()) {
          try {
            TextSecureEnvelope envelope = pipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            Log.w(TAG, "Retrieved envelope! " + envelope.getSource());
          } catch (TimeoutException | InvalidVersionException e) {
            Log.w(TAG, e);
          }

          decrementPushReceived();
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      } catch (Exception e) {
        Log.w(TAG, e);
      } finally {
        Log.w(TAG, "Shutting down pipe...");
        shutdown(pipe);
      }

      Log.w(TAG, "Looping...");
    }
  }

  @Override
  public void onRequirementStatusChanged() {
    synchronized (this) {
      notifyAll();
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private synchronized void incrementActive() {
    activeActivities++;
    Log.w(TAG, "Active Count: " + activeActivities);
    notifyAll();
  }

  private synchronized void decrementActive() {
    activeActivities--;
    Log.w(TAG, "Active Count: " + activeActivities);
    notifyAll();
  }

  private synchronized void incrementPushReceived() {
    pushPending = true;
    notifyAll();
  }

  private synchronized void decrementPushReceived() {
    pushPending = false;
    notifyAll();
  }

  private synchronized boolean isConnectionNecessary() {
    Log.w(TAG, "Network requirement: " + networkRequirement.isPresent());
    return (activeActivities > 0 || pushPending) && networkRequirement.isPresent();
  }

  private synchronized void waitForConnectionNecessary() {
    try {
      while (!isConnectionNecessary()) wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private void shutdown(TextSecureMessagePipe pipe) {
    try {
      pipe.shutdown();
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    }
  }

  public static void registerActivityStarted(Context activity) {
    Intent intent = new Intent(activity, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_ACTIVITY_STARTED);
    activity.startService(intent);
  }

  public static void registerActivityStopped(Context activity) {
    Intent intent = new Intent(activity, MessageRetrievalService.class);
    intent.setAction(MessageRetrievalService.ACTION_ACTIVITY_FINISHED);
    activity.startService(intent);
  }

  private static class NamedThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "MessageRetrievalService");
    }
  }
}
