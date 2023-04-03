package org.thoughtcrime.securesms.service.webrtc;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder;
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager;
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCommand;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Provide a foreground service for {@link SignalCallManager} to leverage to run in the background when necessary. Also
 * provides devices listeners needed for during a call (i.e., bluetooth, power button).
 */
public final class WebRtcCallService extends Service implements SignalAudioManager.EventListener {

  private static final String TAG                        = Log.tag(WebRtcCallService.class);
  private static final String WEBSOCKET_KEEP_ALIVE_TOKEN = WebRtcCallService.class.getName();

  private static final String ACTION_UPDATE              = "UPDATE";
  private static final String ACTION_STOP                = "STOP";
  private static final String ACTION_DENY_CALL           = "DENY_CALL";
  private static final String ACTION_LOCAL_HANGUP        = "LOCAL_HANGUP";
  private static final String ACTION_CHANGE_POWER_BUTTON = "CHANGE_POWER_BUTTON";
  private static final String ACTION_SEND_AUDIO_COMMAND  = "SEND_AUDIO_COMMAND";

  private static final String EXTRA_UPDATE_TYPE   = "UPDATE_TYPE";
  private static final String EXTRA_RECIPIENT_ID  = "RECIPIENT_ID";
  private static final String EXTRA_ENABLED       = "ENABLED";
  private static final String EXTRA_AUDIO_COMMAND = "AUDIO_COMMAND";

  private static final int  INVALID_NOTIFICATION_ID           = -1;
  private static final long REQUEST_WEBSOCKET_STAY_OPEN_DELAY = TimeUnit.MINUTES.toMillis(1);
  private static final long FOREGROUND_SERVICE_TIMEOUT        = TimeUnit.SECONDS.toMillis(10);

  private final WebSocketKeepAliveTask webSocketKeepAliveTask = new WebSocketKeepAliveTask();

  private SignalCallManager callManager;

  private NetworkReceiver                 networkReceiver;
  private PowerButtonReceiver             powerButtonReceiver;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;
  private PhoneStateListener              hangUpRtcOnDeviceCallAnswered;
  private SignalAudioManager              signalAudioManager;
  private int                             lastNotificationId;
  private Notification                    lastNotification;
  private boolean                         isGroup = true;

  public static void update(@NonNull Context context, int type, @NonNull RecipientId recipientId) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_UPDATE)
          .putExtra(EXTRA_UPDATE_TYPE, type)
          .putExtra(EXTRA_RECIPIENT_ID, recipientId);

    ForegroundServiceUtil.startWhenCapableOrThrow(context, intent, FOREGROUND_SERVICE_TIMEOUT);
  }

  public static void denyCall(@NonNull Context context) {
    ForegroundServiceUtil.startWhenCapableOrThrow(context, denyCallIntent(context), FOREGROUND_SERVICE_TIMEOUT);
  }

  public static void hangup(@NonNull Context context) {
    ForegroundServiceUtil.startWhenCapableOrThrow(context, hangupIntent(context), FOREGROUND_SERVICE_TIMEOUT);
  }

  public static void stop(@NonNull Context context) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_STOP);

    ForegroundServiceUtil.startWhenCapableOrThrow(context, intent, FOREGROUND_SERVICE_TIMEOUT);
  }

  public static @NonNull Intent denyCallIntent(@NonNull Context context) {
    return new Intent(context, WebRtcCallService.class).setAction(ACTION_DENY_CALL);
  }

  public static @NonNull Intent hangupIntent(@NonNull Context context) {
    return new Intent(context, WebRtcCallService.class).setAction(ACTION_LOCAL_HANGUP);
  }

  public static void sendAudioManagerCommand(@NonNull Context context, @NonNull AudioManagerCommand command) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_SEND_AUDIO_COMMAND)
          .putExtra(EXTRA_AUDIO_COMMAND, command);
    ForegroundServiceUtil.startWhenCapableOrThrow(context, intent, FOREGROUND_SERVICE_TIMEOUT);
  }

  public static void changePowerButtonReceiver(@NonNull Context context, boolean register) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_CHANGE_POWER_BUTTON)
          .putExtra(EXTRA_ENABLED, register);

    ForegroundServiceUtil.startWhenCapableOrThrow(context, intent, FOREGROUND_SERVICE_TIMEOUT);
  }

  @Override
  public void onCreate() {
    Log.v(TAG, "onCreate");
    super.onCreate();
    this.callManager                   = ApplicationDependencies.getSignalCallManager();
    this.hangUpRtcOnDeviceCallAnswered = new HangUpRtcOnPstnCallAnsweredListener();
    this.lastNotificationId            = INVALID_NOTIFICATION_ID;

    registerUncaughtExceptionHandler();
    registerNetworkReceiver();

    if (!AndroidTelecomUtil.getTelecomSupported()) {
      try {
        TelephonyUtil.getManager(this).listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_CALL_STATE);
      } catch (SecurityException e) {
        Log.w(TAG, "Failed to listen to PSTN call answers!", e);
      }
    }
  }

  @Override
  public void onDestroy() {
    Log.v(TAG, "onDestroy");
    super.onDestroy();

    if (uncaughtExceptionHandlerManager != null) {
      uncaughtExceptionHandlerManager.unregister();
    }

    if (signalAudioManager != null) {
      signalAudioManager.shutdown();
    }

    unregisterNetworkReceiver();
    unregisterPowerButtonReceiver();

    if (!AndroidTelecomUtil.getTelecomSupported()) {
      TelephonyUtil.getManager(this).listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_NONE);
    }

    webSocketKeepAliveTask.stop();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null || intent.getAction() == null) {
      setCallNotification();
      stop();
      return START_NOT_STICKY;
    }

    Log.i(TAG, "action: " + intent.getAction());
    webSocketKeepAliveTask.start();

    switch (intent.getAction()) {
      case ACTION_UPDATE:
        RecipientId recipientId = Objects.requireNonNull(intent.getParcelableExtra(EXTRA_RECIPIENT_ID));
        isGroup = Recipient.resolved(recipientId).isGroup();
        setCallInProgressNotification(intent.getIntExtra(EXTRA_UPDATE_TYPE, 0),
                                      Objects.requireNonNull(intent.getParcelableExtra(EXTRA_RECIPIENT_ID)));
        return START_STICKY;
      case ACTION_SEND_AUDIO_COMMAND:
        setCallNotification();
        if (signalAudioManager == null) {
          signalAudioManager = SignalAudioManager.create(this, this);
        }
        AudioManagerCommand audioCommand = Objects.requireNonNull(intent.getParcelableExtra(EXTRA_AUDIO_COMMAND));
        Log.i(TAG, "Sending audio command [" + audioCommand.getClass().getSimpleName() + "] to " + signalAudioManager.getClass().getSimpleName());
        signalAudioManager.handleCommand(audioCommand);
        return START_STICKY;
      case ACTION_CHANGE_POWER_BUTTON:
        setCallNotification();
        if (intent.getBooleanExtra(EXTRA_ENABLED, false)) {
          registerPowerButtonReceiver();
        } else {
          unregisterPowerButtonReceiver();
        }
        return START_STICKY;
      case ACTION_STOP:
        setCallNotification();
        stop();
        return START_NOT_STICKY;
      case ACTION_DENY_CALL:
        setCallNotification();
        callManager.denyCall();
        return START_NOT_STICKY;
      case ACTION_LOCAL_HANGUP:
        setCallNotification();
        callManager.localHangup();
        return START_NOT_STICKY;
      default:
        throw new AssertionError("Unknown action: " + intent.getAction());
    }
  }

  private void setCallNotification() {
    if (lastNotificationId != INVALID_NOTIFICATION_ID) {
      startForegroundCompat(lastNotificationId, lastNotification);
    } else {
      Log.w(TAG, "Service running without having called start first, show temp notification and terminate service.");
      startForegroundCompat(CallNotificationBuilder.getStartingStoppingNotificationId(), CallNotificationBuilder.getStoppingNotification(this));
      stop();
    }
  }

  public void setCallInProgressNotification(int type, @NonNull RecipientId id) {
    lastNotificationId = CallNotificationBuilder.getNotificationId(type);
    lastNotification   = CallNotificationBuilder.getCallInProgressNotification(this, type, Recipient.resolved(id));

    startForegroundCompat(lastNotificationId, lastNotification);
  }

  private void startForegroundCompat(int notificationId, Notification notification) {
    if (Build.VERSION.SDK_INT >= 30) {
      startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
    } else {
      startForeground(notificationId, notification);
    }
  }

  private void stop() {
    stopForeground(true);
    stopSelf();
  }

  private void registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
    uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(callManager.getLockManager()));
  }

  private void registerNetworkReceiver() {
    if (networkReceiver == null) {
      networkReceiver = new NetworkReceiver();

      registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }
  }

  private void unregisterNetworkReceiver() {
    if (networkReceiver != null) {
      unregisterReceiver(networkReceiver);

      networkReceiver = null;
    }
  }

  public void registerPowerButtonReceiver() {
    if (!AndroidTelecomUtil.getTelecomSupported() && powerButtonReceiver == null) {
      powerButtonReceiver = new PowerButtonReceiver();

      registerReceiver(powerButtonReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
  }

  public void unregisterPowerButtonReceiver() {
    if (powerButtonReceiver != null) {
      unregisterReceiver(powerButtonReceiver);

      powerButtonReceiver = null;
    }
  }

  @Override
  public @Nullable IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onAudioDeviceChanged(@NonNull SignalAudioManager.AudioDevice activeDevice, @NonNull Set<SignalAudioManager.AudioDevice> availableDevices) {
    callManager.onAudioDeviceChanged(activeDevice, availableDevices);
  }

  @Override
  public void onBluetoothPermissionDenied() {
    callManager.onBluetoothPermissionDenied();
  }

  @SuppressWarnings("deprecation")
  private class HangUpRtcOnPstnCallAnsweredListener extends PhoneStateListener {
    @Override
    public void onCallStateChanged(int state, @NonNull String phoneNumber) {
      super.onCallStateChanged(state, phoneNumber);
      if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
        hangup();
        Log.i(TAG, "Device phone call ended Signal call.");
      }
    }

    private void hangup() {
      callManager.localHangup();
    }
  }

  /**
   * Periodically request the web socket stay open if we are doing anything call related.
   */
  private class WebSocketKeepAliveTask implements Runnable {
    private boolean keepRunning = false;

    @MainThread
    public void start() {
      if (!keepRunning) {
        keepRunning = true;
        run();
      }
    }

    @MainThread
    public void stop() {
      keepRunning = false;
      ThreadUtil.cancelRunnableOnMain(webSocketKeepAliveTask);
      ApplicationDependencies.getIncomingMessageObserver().removeKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN);
    }

    @MainThread
    @Override
    public void run() {
      if (keepRunning) {
        ApplicationDependencies.getIncomingMessageObserver().registerKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN);
        ThreadUtil.runOnMainDelayed(this, REQUEST_WEBSOCKET_STAY_OPEN_DELAY);
      }
    }
  }

  private static class NetworkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();

      ApplicationDependencies.getSignalCallManager().networkChange(activeNetworkInfo != null && activeNetworkInfo.isConnected());
      ApplicationDependencies.getSignalCallManager().bandwidthModeUpdate();
    }
  }

  private static class PowerButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
      if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
        ApplicationDependencies.getSignalCallManager().screenOff();
      }
    }
  }

  private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
    private final LockManager lockManager;

    private ProximityLockRelease(@NonNull LockManager lockManager) {
      this.lockManager = lockManager;
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
      Log.i(TAG, "Uncaught exception - releasing proximity lock", throwable);
      lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
    }
  }
}
