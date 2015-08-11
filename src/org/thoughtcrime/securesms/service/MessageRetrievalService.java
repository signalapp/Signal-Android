package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.gcm.GcmBroadcastReceiver;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.textsecure.api.TextSecureMessagePipe;
import org.whispersystems.textsecure.api.TextSecureMessageReceiver;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

public class MessageRetrievalService extends Service implements Runnable, InjectableType, RequirementListener {

  private static final String TAG = MessageRetrievalService.class.getSimpleName();

  public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
  public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";
  public static final  String ACTION_PUSH_RECEIVED     = "PUSH_RECEIVED";
  private static final long   REQUEST_TIMEOUT_MINUTES  = 1;

  private NetworkRequirement         networkRequirement;
  private NetworkRequirementProvider networkRequirementProvider;

  @Inject
  public TextSecureMessageReceiver receiver;

  private int          activeActivities = 0;
  private List<Intent> pushPending      = new LinkedList<>();

  @Override
  public void onCreate() {
    super.onCreate();
    ApplicationContext.getInstance(this).injectDependencies(this);

    networkRequirement         = new NetworkRequirement(this);
    networkRequirementProvider = new NetworkRequirementProvider(this);

    networkRequirementProvider.setListener(this);
    new Thread(this, "MessageRetrievalService").start();
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_STICKY;

    if      (ACTION_ACTIVITY_STARTED.equals(intent.getAction()))  incrementActive();
    else if (ACTION_ACTIVITY_FINISHED.equals(intent.getAction())) decrementActive();
    else if (ACTION_PUSH_RECEIVED.equals(intent.getAction()))     incrementPushReceived(intent);

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
            Log.w(TAG, "Reading message...");
            pipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                      new TextSecureMessagePipe.MessagePipeCallback() {
                        @Override
                        public void onMessage(TextSecureEnvelope envelope) {
                          Log.w(TAG, "Retrieved envelope! " + envelope.getSource());

                          PushContentReceiveJob receiveJob = new PushContentReceiveJob(MessageRetrievalService.this);
                          receiveJob.handle(envelope, false);

                          decrementPushReceived();
                        }
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

  private synchronized void incrementPushReceived(Intent intent) {
    pushPending.add(intent);
    notifyAll();
  }

  private synchronized void decrementPushReceived() {
    if (!pushPending.isEmpty()) {
      Intent intent = pushPending.remove(0);
      GcmBroadcastReceiver.completeWakefulIntent(intent);
      notifyAll();
    }
  }

  private synchronized boolean isConnectionNecessary() {
    Log.w(TAG, String.format("Network requirement: %s, active activities: %s, push pending: %s",
                             networkRequirement.isPresent(), activeActivities, pushPending.size()));

    return TextSecurePreferences.isWebsocketRegistered(this)                                                &&
           (activeActivities > 0 || !pushPending.isEmpty() || !TextSecurePreferences.isGcmRegistered(this)) &&
           networkRequirement.isPresent();
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
    } catch (Throwable t) {
      Log.w(TAG, t);
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
}
