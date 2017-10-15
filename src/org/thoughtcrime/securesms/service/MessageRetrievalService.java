package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.gcm.GcmBroadcastReceiver;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

public class MessageRetrievalService extends Service implements InjectableType, RequirementListener {

  private static final String TAG = MessageRetrievalService.class.getSimpleName();

  public static final  String ACTION_ACTIVITY_STARTED  = "ACTIVITY_STARTED";
  public static final  String ACTION_ACTIVITY_FINISHED = "ACTIVITY_FINISHED";
  public static final  String ACTION_PUSH_RECEIVED     = "PUSH_RECEIVED";
  public static final  String ACTION_INITIALIZE        = "INITIALIZE";
  public static final  int    FOREGROUND_ID            = 313399;

  private static final long   REQUEST_TIMEOUT_MINUTES  = 1;

  private NetworkRequirement         networkRequirement;
  private NetworkRequirementProvider networkRequirementProvider;

  private ProdAlarmReceiver prodAlarmReceiver = null;

  @Inject
  public SignalServiceMessageReceiver receiver;

  private int                    activeActivities = 0;
  private List<Intent>           pushPending      = new LinkedList<>();
  private MessageRetrievalThread retrievalThread  = null;

  public static SignalServiceMessagePipe pipe = null;

  @Override
  public void onCreate() {
    super.onCreate();
    ApplicationContext.getInstance(this).injectDependencies(this);

    networkRequirement         = new NetworkRequirement(this);
    networkRequirementProvider = new NetworkRequirementProvider(this);

    networkRequirementProvider.setListener(this);

    retrievalThread = new MessageRetrievalThread();
    retrievalThread.start();

    setForegroundIfNecessary();
    enableProddingIfNecessary();
  }

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_STICKY;

    if      (ACTION_ACTIVITY_STARTED.equals(intent.getAction()))  incrementActive();
    else if (ACTION_ACTIVITY_FINISHED.equals(intent.getAction())) decrementActive();
    else if (ACTION_PUSH_RECEIVED.equals(intent.getAction()))     incrementPushReceived(intent);

    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    if (retrievalThread != null) {
      retrievalThread.stopThread();
    }

    if (prodAlarmReceiver != null) {
      prodAlarmReceiver.stop();
      unregisterReceiver(prodAlarmReceiver);
    }

    sendBroadcast(new Intent("org.thoughtcrime.securesms.RESTART"));
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

  private void enableProddingIfNecessary() {
    if (TextSecurePreferences.isGcmDisabled(this)) {
      prodAlarmReceiver = new ProdAlarmReceiver();
      registerReceiver(prodAlarmReceiver,
                       new IntentFilter(ProdAlarmReceiver.WAKE_UP_THREADS_ACTION));
    }
  }

  private void setForegroundIfNecessary() {
    if (TextSecurePreferences.isGcmDisabled(this)) {
      NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
      builder.setContentTitle(getString(R.string.MessageRetrievalService_signal));
      builder.setContentText(getString(R.string.MessageRetrievalService_background_connection_enabled));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
      builder.setWhen(0);
      builder.setSmallIcon(R.drawable.ic_signal_grey_24dp);
      startForeground(FOREGROUND_ID, builder.build());
    }
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
    boolean isGcmDisabled = TextSecurePreferences.isGcmDisabled(this);

    Log.w(TAG, String.format("Network requirement: %s, active activities: %s, push pending: %s, gcm disabled: %b",
                             networkRequirement.isPresent(), activeActivities, pushPending.size(), isGcmDisabled));

    return TextSecurePreferences.isPushRegistered(this)                       &&
           TextSecurePreferences.isWebsocketRegistered(this)                  &&
           (activeActivities > 0 || !pushPending.isEmpty() || isGcmDisabled)  &&
           networkRequirement.isPresent();
  }

  private synchronized void waitForConnectionNecessary() {
    try {
      while (!isConnectionNecessary()) wait();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private void shutdown(SignalServiceMessagePipe pipe) {
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

  public static @Nullable SignalServiceMessagePipe getPipe() {
    return pipe;
  }

  public class ProdAlarmReceiver extends BroadcastReceiver {

    private final int PRODDING_TIMEOUT_SECONDS = 60;

    private int pipes;

    public static final String WAKE_UP_THREADS_ACTION = "org.thoughtcrime.securesms.ProdAlarReceiver.WAKE_UP_THREADS";

    private void setOrCancelAlarm(Context context, boolean set) {
      Intent        intent        = new Intent(WAKE_UP_THREADS_ACTION);
      PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
      AlarmManager  alarmManager  = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

      alarmManager.cancel(pendingIntent);

      if (set) {
        Log.w(TAG, "Setting repeating alarm to prod the message pipe.");

        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                  SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(PRODDING_TIMEOUT_SECONDS),
                                  TimeUnit.SECONDS.toMillis(PRODDING_TIMEOUT_SECONDS),
                                  pendingIntent);
      } else {
        Log.w(TAG, "Canceling message pipe prodding alarm.");
      }
    }

    public synchronized void incrementPipes() {
      if (pipes < 0) {
        return;
      }

      pipes++;

      if (pipes == 1) {
        setOrCancelAlarm(MessageRetrievalService.this, true);
      }
    }

    public synchronized void decrementPipes() {
      if (pipes < 0) {
        return;
      }

      pipes--;

      assert (pipes >= 0);

      if (pipes == 0) {
        setOrCancelAlarm(MessageRetrievalService.this, false);
      }
    }

    public synchronized void stop() {
      pipes = -1;
      setOrCancelAlarm(MessageRetrievalService.this, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(TAG, "Prodding the message pipe.");
      SignalServiceMessagePipe.prod();
    }

  }

  private class MessageRetrievalThread extends Thread implements Thread.UncaughtExceptionHandler {

    private AtomicBoolean stopThread = new AtomicBoolean(false);

    MessageRetrievalThread() {
      super("MessageRetrievalService");
      setUncaughtExceptionHandler(this);
    }

    @Override
    public void run() {
      while (!stopThread.get()) {
        Log.w(TAG, "Waiting for websocket state change....");
        waitForConnectionNecessary();

        Log.w(TAG, "Making websocket connection....");
        pipe = receiver.createMessagePipe();

        SignalServiceMessagePipe localPipe = pipe;

        if (prodAlarmReceiver != null) {
          prodAlarmReceiver.incrementPipes();
        }

        try {
          while (isConnectionNecessary() && !stopThread.get()) {
            try {
              Log.w(TAG, "Reading message...");
              localPipe.read(REQUEST_TIMEOUT_MINUTES, TimeUnit.MINUTES,
                             new SignalServiceMessagePipe.MessagePipeCallback() {
                               @Override
                               public void onMessage(SignalServiceEnvelope envelope) {
                                 Log.w(TAG, "Retrieved envelope! " + envelope.getSource());

                                 PushContentReceiveJob receiveJob = new PushContentReceiveJob(MessageRetrievalService.this);
                                 receiveJob.handle(envelope);

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

          if (prodAlarmReceiver != null) {
            prodAlarmReceiver.decrementPipes();
          }

          shutdown(localPipe);
        }

        Log.w(TAG, "Looping...");
      }

      Log.w(TAG, "Exiting...");
    }

    private void stopThread() {
      stopThread.set(true);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      Log.w(TAG, "*** Uncaught exception!");
      Log.w(TAG, e);
    }
  }
}
