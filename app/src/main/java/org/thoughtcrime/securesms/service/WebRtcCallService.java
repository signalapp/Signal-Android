package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.CallManager.CallEvent;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.HttpHeader;
import org.signal.ringrtc.IceCandidate;
import org.signal.ringrtc.Remote;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.zkgroup.VerificationFailedException;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobs.GroupCallUpdateSendJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.ringrtc.TurnServerInfoParcel;
import org.thoughtcrime.securesms.service.webrtc.IdleActionProcessor;
import org.thoughtcrime.securesms.service.webrtc.WebRtcInteractor;
import org.thoughtcrime.securesms.service.webrtc.WebRtcUtil;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder;
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager;
import org.thoughtcrime.securesms.webrtc.audio.BluetoothStateManager;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WebRtcCallService extends Service implements CallManager.Observer,
        BluetoothStateManager.BluetoothStateListener,
        CameraEventListener,
        GroupCall.Observer
{

  private static final String TAG = WebRtcCallService.class.getSimpleName();

  public static final String EXTRA_MUTE                       = "mute_value";
  public static final String EXTRA_AVAILABLE                  = "enabled_value";
  public static final String EXTRA_SERVER_RECEIVED_TIMESTAMP  = "server_received_timestamp";
  public static final String EXTRA_SERVER_DELIVERED_TIMESTAMP = "server_delivered_timestamp";
  public static final String EXTRA_CALL_ID                    = "call_id";
  public static final String EXTRA_RESULT_RECEIVER            = "result_receiver";
  public static final String EXTRA_SPEAKER                    = "audio_speaker";
  public static final String EXTRA_BLUETOOTH                  = "audio_bluetooth";
  public static final String EXTRA_REMOTE_PEER                = "remote_peer";
  public static final String EXTRA_REMOTE_PEER_KEY            = "remote_peer_key";
  public static final String EXTRA_REMOTE_DEVICE              = "remote_device";
  public static final String EXTRA_REMOTE_IDENTITY_KEY        = "remote_identity_key";
  public static final String EXTRA_OFFER_OPAQUE               = "offer_opaque";
  public static final String EXTRA_OFFER_SDP                  = "offer_sdp";
  public static final String EXTRA_OFFER_TYPE                 = "offer_type";
  public static final String EXTRA_MULTI_RING                 = "multi_ring";
  public static final String EXTRA_HANGUP_TYPE                = "hangup_type";
  public static final String EXTRA_HANGUP_IS_LEGACY           = "hangup_is_legacy";
  public static final String EXTRA_HANGUP_DEVICE_ID           = "hangup_device_id";
  public static final String EXTRA_ANSWER_OPAQUE              = "answer_opaque";
  public static final String EXTRA_ANSWER_SDP                 = "answer_sdp";
  public static final String EXTRA_ICE_CANDIDATES             = "ice_candidates";
  public static final String EXTRA_ENABLE                     = "enable_value";
  public static final String EXTRA_BROADCAST                  = "broadcast";
  public static final String EXTRA_ANSWER_WITH_VIDEO          = "enable_video";
  public static final String EXTRA_ERROR_CALL_STATE           = "error_call_state";
  public static final String EXTRA_ERROR_IDENTITY_KEY         = "remote_identity_key";
  public static final String EXTRA_CAMERA_STATE               = "camera_state";
  public static final String EXTRA_IS_ALWAYS_TURN             = "is_always_turn";
  public static final String EXTRA_TURN_SERVER_INFO           = "turn_server_info";
  public static final String EXTRA_GROUP_EXTERNAL_TOKEN       = "group_external_token";
  public static final String EXTRA_HTTP_REQUEST_ID            = "http_request_id";
  public static final String EXTRA_HTTP_RESPONSE_STATUS       = "http_response_status";
  public static final String EXTRA_HTTP_RESPONSE_BODY         = "http_response_body";
  public static final String EXTRA_OPAQUE_MESSAGE             = "opaque";
  public static final String EXTRA_UUID                       = "uuid";
  public static final String EXTRA_MESSAGE_AGE_SECONDS        = "message_age_seconds";
  public static final String EXTRA_GROUP_CALL_END_REASON      = "group_call_end_reason";
  public static final String EXTRA_GROUP_CALL_HASH            = "group_call_hash";
  public static final String EXTRA_GROUP_CALL_UPDATE_SENDER   = "group_call_update_sender";
  public static final String EXTRA_GROUP_CALL_UPDATE_GROUP    = "group_call_update_group";
  public static final String EXTRA_GROUP_CALL_ERA_ID          = "era_id";
  public static final String EXTRA_RECIPIENT_IDS              = "recipient_ids";

  public static final String ACTION_PRE_JOIN_CALL                       = "CALL_PRE_JOIN";
  public static final String ACTION_CANCEL_PRE_JOIN_CALL                = "CANCEL_PRE_JOIN_CALL";
  public static final String ACTION_OUTGOING_CALL                       = "CALL_OUTGOING";
  public static final String ACTION_DENY_CALL                           = "DENY_CALL";
  public static final String ACTION_LOCAL_HANGUP                        = "LOCAL_HANGUP";
  public static final String ACTION_SET_MUTE_AUDIO                      = "SET_MUTE_AUDIO";
  public static final String ACTION_FLIP_CAMERA                         = "FLIP_CAMERA";
  public static final String ACTION_BLUETOOTH_CHANGE                    = "BLUETOOTH_CHANGE";
  public static final String ACTION_NETWORK_CHANGE                      = "NETWORK_CHANGE";
  public static final String ACTION_BANDWIDTH_MODE_UPDATE               = "BANDWIDTH_MODE_UPDATE";
  public static final String ACTION_WIRED_HEADSET_CHANGE                = "WIRED_HEADSET_CHANGE";
  public static final String ACTION_SCREEN_OFF                          = "SCREEN_OFF";
  public static final String ACTION_IS_IN_CALL_QUERY                    = "IS_IN_CALL";
  public static final String ACTION_SET_AUDIO_SPEAKER                   = "SET_AUDIO_SPEAKER";
  public static final String ACTION_SET_AUDIO_BLUETOOTH                 = "SET_AUDIO_BLUETOOTH";
  public static final String ACTION_CALL_CONNECTED                      = "CALL_CONNECTED";
  public static final String ACTION_START_OUTGOING_CALL                 = "START_OUTGOING_CALL";
  public static final String ACTION_START_INCOMING_CALL                 = "START_INCOMING_CALL";
  public static final String ACTION_LOCAL_RINGING                       = "LOCAL_RINGING";
  public static final String ACTION_REMOTE_RINGING                      = "REMOTE_RINGING";
  public static final String ACTION_ACCEPT_CALL                         = "ACCEPT_CALL";
  public static final String ACTION_SEND_OFFER                          = "SEND_OFFER";
  public static final String ACTION_SEND_ANSWER                         = "SEND_ANSWER";
  public static final String ACTION_SEND_ICE_CANDIDATES                 = "SEND_ICE_CANDIDATES";
  public static final String ACTION_SEND_HANGUP                         = "SEND_HANGUP";
  public static final String ACTION_SEND_BUSY                           = "SEND_BUSY";
  public static final String ACTION_RECEIVE_OFFER                       = "RECEIVE_OFFER";
  public static final String ACTION_RECEIVE_ANSWER                      = "RECEIVE_ANSWER";
  public static final String ACTION_RECEIVE_ICE_CANDIDATES              = "RECEIVE_ICE_CANDIDATES";
  public static final String ACTION_RECEIVE_HANGUP                      = "RECEIVE_HANGUP";
  public static final String ACTION_RECEIVE_BUSY                        = "RECEIVE_BUSY";
  public static final String ACTION_REMOTE_VIDEO_ENABLE                 = "REMOTE_VIDEO_ENABLE";
  public static final String ACTION_SET_ENABLE_VIDEO                    = "SET_ENABLE_VIDEO";
  public static final String ACTION_ENDED_REMOTE_HANGUP                 = "ENDED_REMOTE_HANGUP";
  public static final String ACTION_ENDED_REMOTE_HANGUP_ACCEPTED        = "ENDED_REMOTE_HANGUP_ACCEPTED";
  public static final String ACTION_ENDED_REMOTE_HANGUP_DECLINED        = "ENDED_REMOTE_HANGUP_DECLINED";
  public static final String ACTION_ENDED_REMOTE_HANGUP_BUSY            = "ENDED_REMOTE_HANGUP_BUSY";
  public static final String ACTION_ENDED_REMOTE_HANGUP_NEED_PERMISSION = "ENDED_REMOTE_HANGUP_NEED_PERMISSION";
  public static final String ACTION_ENDED_REMOTE_BUSY                   = "ENDED_REMOTE_BUSY";
  public static final String ACTION_ENDED_REMOTE_GLARE                  = "ENDED_REMOTE_GLARE";
  public static final String ACTION_ENDED_TIMEOUT                       = "ENDED_TIMEOUT";
  public static final String ACTION_ENDED_INTERNAL_FAILURE              = "ENDED_INTERNAL_FAILURE";
  public static final String ACTION_ENDED_SIGNALING_FAILURE             = "ENDED_SIGNALING_FAILURE";
  public static final String ACTION_ENDED_CONNECTION_FAILURE            = "ENDED_CONNECTION_FAILURE";
  public static final String ACTION_RECEIVED_OFFER_EXPIRED              = "RECEIVED_OFFER_EXPIRED";
  public static final String ACTION_RECEIVED_OFFER_WHILE_ACTIVE         = "RECEIVED_OFFER_WHILE_ACTIVE";
  public static final String ACTION_CALL_CONCLUDED                      = "CALL_CONCLUDED";
  public static final String ACTION_MESSAGE_SENT_SUCCESS                = "MESSAGE_SENT_SUCCESS";
  public static final String ACTION_MESSAGE_SENT_ERROR                  = "MESSAGE_SENT_ERROR";
  public static final String ACTION_CAMERA_SWITCH_COMPLETED             = "CAMERA_FLIP_COMPLETE";
  public static final String ACTION_TURN_SERVER_UPDATE                  = "TURN_SERVER_UPDATE";
  public static final String ACTION_SETUP_FAILURE                       = "SETUP_FAILURE";
  public static final String ACTION_HTTP_SUCCESS                        = "HTTP_SUCCESS";
  public static final String ACTION_HTTP_FAILURE                        = "HTTP_FAILURE";
  public static final String ACTION_SEND_OPAQUE_MESSAGE                 = "SEND_OPAQUE_MESSAGE";
  public static final String ACTION_RECEIVE_OPAQUE_MESSAGE              = "RECEIVE_OPAQUE_MESSAGE";

  public static final String ACTION_GROUP_LOCAL_DEVICE_STATE_CHANGED  = "GROUP_LOCAL_DEVICE_CHANGE";
  public static final String ACTION_GROUP_REMOTE_DEVICE_STATE_CHANGED = "GROUP_REMOTE_DEVICE_CHANGE";
  public static final String ACTION_GROUP_JOINED_MEMBERSHIP_CHANGED   = "GROUP_JOINED_MEMBERS_CHANGE";
  public static final String ACTION_GROUP_REQUEST_MEMBERSHIP_PROOF    = "GROUP_REQUEST_MEMBERSHIP_PROOF";
  public static final String ACTION_GROUP_REQUEST_UPDATE_MEMBERS      = "GROUP_REQUEST_UPDATE_MEMBERS";
  public static final String ACTION_GROUP_UPDATE_RENDERED_RESOLUTIONS = "GROUP_UPDATE_RENDERED_RESOLUTIONS";
  public static final String ACTION_GROUP_CALL_ENDED                  = "GROUP_CALL_ENDED";
  public static final String ACTION_GROUP_CALL_PEEK                   = "GROUP_CALL_PEEK";
  public static final String ACTION_GROUP_MESSAGE_SENT_ERROR          = "GROUP_MESSAGE_SENT_ERROR";
  public static final String ACTION_GROUP_APPROVE_SAFETY_CHANGE       = "GROUP_APPROVE_SAFETY_CHANGE";

  public static final int BUSY_TONE_LENGTH = 2000;

  private SignalServiceMessageSender      messageSender;
  private SignalServiceAccountManager     accountManager;
  private BluetoothStateManager           bluetoothStateManager;
  private WiredHeadsetStateReceiver       wiredHeadsetStateReceiver;
  private NetworkReceiver                 networkReceiver;
  private PowerButtonReceiver             powerButtonReceiver;
  private LockManager                     lockManager;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;
  private WebRtcInteractor                webRtcInteractor;

  @Nullable private CallManager callManager;

  private final ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

  private final PhoneStateListener hangUpRtcOnDeviceCallAnswered = new HangUpRtcOnPstnCallAnsweredListener();

  private WebRtcServiceState serviceState;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "onCreate");

    boolean successful = initializeResources();
    if (!successful) {
      stopSelf();
      return;
    }

    serviceState = new WebRtcServiceState(new IdleActionProcessor(webRtcInteractor));

    registerUncaughtExceptionHandler();
    registerWiredHeadsetStateReceiver();
    registerNetworkReceiver();

    TelephonyUtil.getManager(this)
                 .listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_CALL_STATE);
  }

  private boolean initializeResources() {
    this.messageSender         = ApplicationDependencies.getSignalServiceMessageSender();
    this.accountManager        = ApplicationDependencies.getSignalServiceAccountManager();
    this.lockManager           = new LockManager(this);
    this.bluetoothStateManager = new BluetoothStateManager(this, this);

    this.messageSender.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));
    this.accountManager.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));

    try {
      this.callManager = Objects.requireNonNull(CallManager.createCallManager(this));
    } catch  (NullPointerException | CallException e) {
      Log.e(TAG, "Unable to create Call Manager: ", e);
      return false;
    }

    webRtcInteractor = new WebRtcInteractor(this,
                                            callManager,
                                            lockManager,
                                            new SignalAudioManager(this),
                                            bluetoothStateManager,
                                            this,
                                            this);
    return true;
  }

  @Override
  public int onStartCommand(final @Nullable Intent intent, int flags, int startId) {
    Log.i(TAG, "onStartCommand... action: " + (intent == null ? "NA" : intent.getAction()));
    if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

    serviceExecutor.execute(() -> {
      Log.d(TAG, "action: " + intent.getAction() + " action handler: " + serviceState.getActionProcessor().getTag());
      try {
        WebRtcServiceState previous = serviceState;
        serviceState = serviceState.getActionProcessor().processAction(intent.getAction(), intent, serviceState);

        if (previous != serviceState) {
          if (serviceState.getCallInfoState().getCallState() != WebRtcViewModel.State.IDLE) {
            sendMessage();
          }
        }
      } catch (AssertionError e) {
        throw new AssertionError("Invalid state for action: " + intent.getAction() + " processor: " + serviceState.getActionProcessor().getTag(), e);
      }
    });

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.i(TAG, "onDestroy");

    if (callManager != null) {
      try {
        callManager.close();
      } catch (CallException e) {
        Log.w(TAG, "Unable to close call manager: ", e);
      }
      callManager = null;
    }

    if (uncaughtExceptionHandlerManager != null) {
      uncaughtExceptionHandlerManager.unregister();
    }

    if (bluetoothStateManager != null) {
      bluetoothStateManager.onDestroy();
    }

    if (wiredHeadsetStateReceiver != null) {
      unregisterReceiver(wiredHeadsetStateReceiver);
      wiredHeadsetStateReceiver = null;
    }

    if (powerButtonReceiver != null) {
      unregisterReceiver(powerButtonReceiver);
      powerButtonReceiver = null;
    }

    unregisterNetworkReceiver();

    TelephonyUtil.getManager(this)
                 .listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_NONE);
  }

  @Override
  public void onBluetoothStateChanged(boolean isAvailable) {
    Log.i(TAG, "onBluetoothStateChanged: " + isAvailable);

    Intent intent = new Intent(this, WebRtcCallService.class);
    intent.setAction(ACTION_BLUETOOTH_CHANGE);
    intent.putExtra(EXTRA_AVAILABLE, isAvailable);

    startService(intent);
  }

  @Override
  public void onCameraSwitchCompleted(@NonNull CameraState newCameraState) {
    Intent intent = new Intent(this, WebRtcCallService.class);
    intent.setAction(ACTION_CAMERA_SWITCH_COMPLETED)
          .putExtra(EXTRA_CAMERA_STATE, newCameraState);

    startService(intent);
  }

  private void registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
    uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(lockManager));
  }

  private void registerWiredHeadsetStateReceiver() {
    wiredHeadsetStateReceiver = new WiredHeadsetStateReceiver();

    String action;

    if (Build.VERSION.SDK_INT >= 21) {
      action = AudioManager.ACTION_HEADSET_PLUG;
    } else {
      action = Intent.ACTION_HEADSET_PLUG;
    }

    registerReceiver(wiredHeadsetStateReceiver, new IntentFilter(action));
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
    if (powerButtonReceiver == null) {
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

  public void insertMissedCall(@NonNull RemotePeer remotePeer, boolean signal, long timestamp, boolean isVideoOffer) {
    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getSmsDatabase(this).insertMissedCall(remotePeer.getId(), timestamp, isVideoOffer);
    ApplicationDependencies.getMessageNotifier().updateNotification(this, messageAndThreadId.second(), signal);
  }

  public void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    retrieveTurnServers().addListener(new FutureTaskListener<TurnServerInfoParcel>() {
      @Override
      public void onSuccess(@Nullable TurnServerInfoParcel result) {
        boolean isAlwaysTurn = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);

        Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
        intent.setAction(ACTION_TURN_SERVER_UPDATE)
              .putExtra(EXTRA_IS_ALWAYS_TURN, isAlwaysTurn)
              .putExtra(EXTRA_TURN_SERVER_INFO, result);

        startService(intent);
      }

      @Override
      public void onFailure(@NonNull ExecutionException exception) {
        Log.w(TAG, "Unable to retrieve turn servers: ", exception);

        Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
        intent.setAction(ACTION_SETUP_FAILURE)
              .putExtra(EXTRA_CALL_ID, remotePeer.getCallId().longValue());

        startService(intent);
      }
    });
  }

  public void setCallInProgressNotification(int type, @NonNull Recipient recipient) {
    startForeground(CallNotificationBuilder.getNotificationId(getApplicationContext(), type),
                    CallNotificationBuilder.getCallInProgressNotification(this, type, recipient));
  }

  public void sendMessage() {
    sendMessage(serviceState);
  }

  public void sendMessage(@NonNull WebRtcServiceState state) {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state));
  }

  private @NonNull ListenableFutureTask<Boolean> sendMessage(@NonNull final RemotePeer remotePeer,
                                                             @NonNull final SignalServiceCallMessage callMessage)
  {
    Callable<Boolean> callable = () -> {
      Recipient recipient = remotePeer.getRecipient();
      if (recipient.isBlocked()) {
        return true;
      }

      messageSender.sendCallMessage(RecipientUtil.toSignalServiceAddress(WebRtcCallService.this, recipient),
                                    UnidentifiedAccessUtil.getAccessFor(WebRtcCallService.this, recipient),
                                    callMessage);
      return true;
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  public void startCallCardActivityIfPossible() {
    if (Build.VERSION.SDK_INT >= 29 && !ApplicationContext.getInstance(getApplicationContext()).isAppVisible()) {
      return;
    }

    Intent activityIntent = new Intent();
    activityIntent.setClass(this, WebRtcCallActivity.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(activityIntent);
  }

  private static @NonNull OfferMessage.Type getOfferTypeFromCallMediaType(@NonNull CallManager.CallMediaType mediaType) {
    return mediaType == CallManager.CallMediaType.VIDEO_CALL ? OfferMessage.Type.VIDEO_CALL : OfferMessage.Type.AUDIO_CALL;
  }

  private static @NonNull HangupMessage.Type getHangupTypeFromCallHangupType(@NonNull CallManager.HangupType hangupType) {
    switch (hangupType) {
      case ACCEPTED:
        return HangupMessage.Type.ACCEPTED;
      case BUSY:
        return HangupMessage.Type.BUSY;
      case NORMAL:
        return HangupMessage.Type.NORMAL;
      case DECLINED:
        return HangupMessage.Type.DECLINED;
      case NEED_PERMISSION:
        return HangupMessage.Type.NEED_PERMISSION;
      default:
        throw new IllegalArgumentException("Unexpected hangup type: " + hangupType);
    }
  }

  @Override
  public @Nullable IBinder onBind(@NonNull Intent intent) {
    return null;
  }

  private static class WiredHeadsetStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
      int state = intent.getIntExtra("state", -1);

      Intent serviceIntent = new Intent(context, WebRtcCallService.class);
      serviceIntent.setAction(ACTION_WIRED_HEADSET_CHANGE);
      serviceIntent.putExtra(WebRtcCallService.EXTRA_AVAILABLE, state != 0);
      context.startService(serviceIntent);
    }
  }

  private static class NetworkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo         activeNetworkInfo   = connectivityManager.getActiveNetworkInfo();
      Intent              serviceIntent       = new Intent(context, WebRtcCallService.class);

      serviceIntent.setAction(ACTION_NETWORK_CHANGE);
      serviceIntent.putExtra(EXTRA_AVAILABLE, activeNetworkInfo != null && activeNetworkInfo.isConnected());
      context.startService(serviceIntent);

      serviceIntent.setAction(ACTION_BANDWIDTH_MODE_UPDATE);
      context.startService(serviceIntent);
    }
  }

  private static class PowerButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
      if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
        Intent serviceIntent = new Intent(context, WebRtcCallService.class);
        serviceIntent.setAction(ACTION_SCREEN_OFF);
        context.startService(serviceIntent);
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

  public static void isCallActive(@NonNull Context context, @NonNull ResultReceiver resultReceiver) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_IS_IN_CALL_QUERY);
    intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);

    context.startService(intent);
  }

  public static void notifyBandwidthModeUpdated(@NonNull Context context) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_BANDWIDTH_MODE_UPDATE);

    context.startService(intent);
  }

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
      if (serviceState != null && serviceState.getCallInfoState().getActivePeer() != null) {
        Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
        intent.setAction(ACTION_LOCAL_HANGUP);

        startService(intent);
      }
    }
  }

  private @NonNull ListenableFutureTask<TurnServerInfoParcel> retrieveTurnServers() {
    Callable<TurnServerInfoParcel> callable = () -> new TurnServerInfoParcel(accountManager.getTurnServerInfo());

    ListenableFutureTask<TurnServerInfoParcel> futureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(futureTask);

    return futureTask;
  }

  private abstract class StateAwareListener<V> implements FutureTaskListener<V> {
    private final CallState expectedState;
    private final CallId    expectedCallId;

    StateAwareListener(@NonNull CallState expectedState, @NonNull CallId expectedCallId) {
      this.expectedState  = expectedState;
      this.expectedCallId = expectedCallId;
    }

    public @NonNull CallId getCallId() {
      return this.expectedCallId;
    }

    @Override
    public void onSuccess(@Nullable V result) {
      if (!isConsistentState()) {
        Log.i(TAG, "State has changed since request, skipping success callback...");
        onStateChangeContinue();
      } else {
        onSuccessContinue(result);
      }
    }

    @Override
    public void onFailure(@NonNull ExecutionException throwable) {
      if (!isConsistentState()) {
        Log.w(TAG, throwable);
        Log.w(TAG, "State has changed since request, skipping failure callback...");
        onStateChangeContinue();
      } else {
        onFailureContinue(throwable.getCause());
      }
    }

    public void onStateChangeContinue() {}

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isConsistentState() {
      RemotePeer activePeer = serviceState.getCallInfoState().getActivePeer();
      return activePeer != null && expectedState == activePeer.getState() && expectedCallId.equals(activePeer.getCallId());
    }

    public abstract void onSuccessContinue(@Nullable V result);
    public abstract void onFailureContinue(@Nullable Throwable throwable);
  }

  private class SendCallMessageListener<V> extends StateAwareListener<V> {
    SendCallMessageListener(@NonNull RemotePeer expectedRemotePeer) {
      super(expectedRemotePeer.getState(), expectedRemotePeer.getCallId());
    }

    @Override
    public void onSuccessContinue(@Nullable V result) {
      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(ACTION_MESSAGE_SENT_SUCCESS);
      intent.putExtra(EXTRA_CALL_ID, getCallId().longValue());

      startService(intent);
    }

    @Override
    public void onStateChangeContinue() {
      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(ACTION_MESSAGE_SENT_SUCCESS)
            .putExtra(EXTRA_CALL_ID, getCallId().longValue());

      startService(intent);
    }

    @Override
    public void onFailureContinue(@Nullable Throwable error) {
      Log.i(TAG, "onFailureContinue: ", error);

      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(ACTION_MESSAGE_SENT_ERROR)
            .putExtra(EXTRA_CALL_ID, getCallId().longValue());

      WebRtcViewModel.State state = WebRtcViewModel.State.NETWORK_FAILURE;

      if (error instanceof UntrustedIdentityException) {
        intent.putExtra(EXTRA_ERROR_IDENTITY_KEY, new IdentityKeyParcelable(((UntrustedIdentityException) error).getIdentityKey()));
        state = WebRtcViewModel.State.UNTRUSTED_IDENTITY;
      } else if (error instanceof UnregisteredUserException) {
        state = WebRtcViewModel.State.NO_SUCH_USER;
      }

      intent.putExtra(EXTRA_ERROR_CALL_STATE, state);

      startService(intent);
    }
  }

  public void sendCallMessage(@NonNull RemotePeer remotePeer, @NonNull SignalServiceCallMessage callMessage) {
    ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(remotePeer, callMessage);
    listenableFutureTask.addListener(new SendCallMessageListener<>(remotePeer));
  }

  public void sendOpaqueCallMessage(@NonNull UUID uuid, @NonNull SignalServiceCallMessage opaqueMessage) {
    RecipientId recipientId = RecipientId.from(uuid, null);
    ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(new RemotePeer(recipientId), opaqueMessage);
    listenableFutureTask.addListener(new FutureTaskListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        // intentionally left blank
      }

      @Override
      public void onFailure(ExecutionException exception) {
        Throwable error = exception.getCause();

        Log.i(TAG, "sendOpaqueCallMessage onFailure: ", error);

        Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
        intent.setAction(ACTION_GROUP_MESSAGE_SENT_ERROR);

        WebRtcViewModel.State state = WebRtcViewModel.State.NETWORK_FAILURE;

        if (error instanceof UntrustedIdentityException) {
          intent.putExtra(EXTRA_ERROR_IDENTITY_KEY, new IdentityKeyParcelable(((UntrustedIdentityException) error).getIdentityKey()));
          state = WebRtcViewModel.State.UNTRUSTED_IDENTITY;
        }

        intent.putExtra(EXTRA_ERROR_CALL_STATE, state);
        intent.putExtra(EXTRA_REMOTE_PEER, new RemotePeer(recipientId));

        startService(intent);
      }
    });
  }

  public void sendGroupCallMessage(@NonNull Recipient recipient, @Nullable String groupCallEraId) {
    SignalExecutors.BOUNDED.execute(() -> ApplicationDependencies.getJobManager().add(GroupCallUpdateSendJob.create(recipient.getId(), groupCallEraId)));
  }

  public void peekGroupCall(@NonNull RecipientId id) {
    networkExecutor.execute(() -> {
      try {
        Recipient               group      = Recipient.resolved(id);
        GroupId.V2              groupId    = group.requireGroupId().requireV2();
        GroupExternalCredential credential = GroupManager.getGroupExternalCredential(this, groupId);

        List<GroupCall.GroupMemberInfo> members = Stream.of(GroupManager.getUuidCipherTexts(this, groupId))
                                                        .map(entry -> new GroupCall.GroupMemberInfo(entry.getKey(), entry.getValue().serialize()))
                                                        .toList();

        //noinspection ConstantConditions
        callManager.peekGroupCall(BuildConfig.SIGNAL_SFU_URL, credential.getTokenBytes().toByteArray(), members, peekInfo -> {
          long threadId = DatabaseFactory.getThreadDatabase(this).getThreadIdFor(group);

          DatabaseFactory.getSmsDatabase(this).updatePreviousGroupCall(threadId,
                                                                       peekInfo.getEraId(),
                                                                       peekInfo.getJoinedMembers(),
                                                                       WebRtcUtil.isCallFull(peekInfo));

          ApplicationDependencies.getMessageNotifier().updateNotification(this, threadId, true, 0, BubbleUtil.BubbleState.HIDDEN);

          EventBus.getDefault().postSticky(new GroupCallPeekEvent(id, peekInfo.getEraId(), peekInfo.getDeviceCount(), peekInfo.getMaxDevices()));
        });

      } catch (IOException | VerificationFailedException | CallException e) {
        Log.e(TAG, "error peeking from active conversation", e);
      }
    });
  }

  public void updateGroupCallUpdateMessage(@NonNull RecipientId groupId, @Nullable String groupCallEraId, @NonNull Collection<UUID> joinedMembers, boolean isCallFull) {
    SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getSmsDatabase(this).insertOrUpdateGroupCall(groupId,
                                                                                                       Recipient.self().getId(),
                                                                                                       System.currentTimeMillis(),
                                                                                                       groupCallEraId,
                                                                                                       joinedMembers,
                                                                                                       isCallFull));
  }

  @Override
  public void onStartCall(@Nullable Remote remote, @NonNull CallId callId, @NonNull Boolean isOutgoing, @Nullable CallManager.CallMediaType callMediaType) {
    Log.i(TAG, "onStartCall(): callId: " + callId + ", outgoing: " + isOutgoing + ", type: " + callMediaType);

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer) remote;
      if (serviceState.getCallInfoState().getPeer(remotePeer.hashCode()) == null) {
        Log.w(TAG, "remotePeer not found in map with key: " + remotePeer.hashCode() + "! Dropping.");
        try {
          callManager.drop(callId);
        } catch (CallException e) {
          serviceState = serviceState.getActionProcessor().callFailure(serviceState, "callManager.drop() failed: ", e);
        }
      }

      remotePeer.setCallId(callId);

      Intent intent = new Intent(this, WebRtcCallService.class);

      if (isOutgoing) {
        intent.setAction(ACTION_START_OUTGOING_CALL);
      } else {
        intent.setAction(ACTION_START_INCOMING_CALL);
      }

      intent.putExtra(EXTRA_REMOTE_PEER_KEY, remotePeer.hashCode());

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onCallEvent(@Nullable Remote remote, @NonNull CallEvent event) {
    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer) remote;
      if (serviceState.getCallInfoState().getPeer(remotePeer.hashCode()) == null) {
        throw new AssertionError("remotePeer not found in map!");
      }

      Log.i(TAG, "onCallEvent(): call_id: " + remotePeer.getCallId() + ", state: " + remotePeer.getState() + ", event: " + event);

      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.putExtra(EXTRA_REMOTE_PEER_KEY, remotePeer.hashCode());

      switch (event) {
        case LOCAL_RINGING:
          intent.setAction(ACTION_LOCAL_RINGING);
          break;
        case REMOTE_RINGING:
          intent.setAction(ACTION_REMOTE_RINGING);
          break;
        case RECONNECTING:
          Log.i(TAG, "Reconnecting: NOT IMPLEMENTED");
          break;
        case RECONNECTED:
          Log.i(TAG, "Reconnected: NOT IMPLEMENTED");
          break;
        case LOCAL_CONNECTED:
        case REMOTE_CONNECTED:
          intent.setAction(ACTION_CALL_CONNECTED);
          break;
        case REMOTE_VIDEO_ENABLE:
          intent.setAction(ACTION_REMOTE_VIDEO_ENABLE)
                .putExtra(EXTRA_ENABLE, true);
          break;
        case REMOTE_VIDEO_DISABLE:
          intent.setAction(ACTION_REMOTE_VIDEO_ENABLE)
                .putExtra(EXTRA_ENABLE, false);
          break;
        case ENDED_REMOTE_HANGUP:
          intent.setAction(ACTION_ENDED_REMOTE_HANGUP);
          break;
        case ENDED_REMOTE_HANGUP_NEED_PERMISSION:
          intent.setAction(ACTION_ENDED_REMOTE_HANGUP_NEED_PERMISSION);
          break;
        case ENDED_REMOTE_HANGUP_ACCEPTED:
          intent.setAction(ACTION_ENDED_REMOTE_HANGUP_ACCEPTED);
          break;
        case ENDED_REMOTE_HANGUP_BUSY:
          intent.setAction(ACTION_ENDED_REMOTE_HANGUP_BUSY);
          break;
        case ENDED_REMOTE_HANGUP_DECLINED:
          intent.setAction(ACTION_ENDED_REMOTE_HANGUP_DECLINED);
          break;
        case ENDED_REMOTE_BUSY:
          intent.setAction(ACTION_ENDED_REMOTE_BUSY);
          break;
        case ENDED_REMOTE_GLARE:
          intent.setAction(ACTION_ENDED_REMOTE_GLARE);
          break;
        case ENDED_TIMEOUT:
          intent.setAction(ACTION_ENDED_TIMEOUT);
          break;
        case ENDED_INTERNAL_FAILURE:
          intent.setAction(ACTION_ENDED_INTERNAL_FAILURE);
          break;
        case ENDED_SIGNALING_FAILURE:
          intent.setAction(ACTION_ENDED_SIGNALING_FAILURE);
          break;
        case ENDED_CONNECTION_FAILURE:
          intent.setAction(ACTION_ENDED_CONNECTION_FAILURE);
          break;
        case RECEIVED_OFFER_EXPIRED:
          intent.setAction(ACTION_RECEIVED_OFFER_EXPIRED);
          break;
        case RECEIVED_OFFER_WHILE_ACTIVE:
        case RECEIVED_OFFER_WITH_GLARE:
          intent.setAction(ACTION_RECEIVED_OFFER_WHILE_ACTIVE);
          break;
        case ENDED_LOCAL_HANGUP:
        case ENDED_APP_DROPPED_CALL:
        case IGNORE_CALLS_FROM_NON_MULTIRING_CALLERS:
          Log.i(TAG, "Ignoring event: " + event);
          return;
        default:
          throw new AssertionError("Unexpected event: " + event.toString());
      }

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onCallConcluded(@Nullable Remote remote) {
    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;

      Log.i(TAG, "onCallConcluded: call_id: " + remotePeer.getCallId());

      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.setAction(ACTION_CALL_CONCLUDED)
            .putExtra(EXTRA_REMOTE_PEER_KEY, remotePeer.hashCode());

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendOffer(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast, @Nullable byte[] opaque, @Nullable String sdp, @NonNull CallManager.CallMediaType callMediaType) {
    Log.i(TAG, "onSendOffer: id: " + callId.format(remoteDevice) + " type: " + callMediaType.name());

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      String     offerType  = getOfferTypeFromCallMediaType(callMediaType).getCode();
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_OFFER)
            .putExtra(EXTRA_CALL_ID,       callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,   remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE, remoteDevice)
            .putExtra(EXTRA_BROADCAST,     broadcast)
            .putExtra(EXTRA_OFFER_OPAQUE,  opaque)
            .putExtra(EXTRA_OFFER_SDP,     sdp)
            .putExtra(EXTRA_OFFER_TYPE,    offerType);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendAnswer(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast, @Nullable byte[] opaque, @Nullable String sdp) {
    Log.i(TAG, "onSendAnswer: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_ANSWER)
            .putExtra(EXTRA_CALL_ID,       callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,   remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE, remoteDevice)
            .putExtra(EXTRA_BROADCAST,     broadcast)
            .putExtra(EXTRA_ANSWER_OPAQUE, opaque)
            .putExtra(EXTRA_ANSWER_SDP,    sdp);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendIceCandidates(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast, @NonNull List<IceCandidate> iceCandidates) {
    Log.i(TAG, "onSendIceCandidates: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      ArrayList<IceCandidateParcel> iceCandidateParcels = new ArrayList<>(iceCandidates.size());
      for (IceCandidate iceCandidate : iceCandidates) {
        iceCandidateParcels.add(new IceCandidateParcel(iceCandidate));
      }

      intent.setAction(ACTION_SEND_ICE_CANDIDATES)
            .putExtra(EXTRA_CALL_ID,       callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,   remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE, remoteDevice)
            .putExtra(EXTRA_BROADCAST,     broadcast)
            .putParcelableArrayListExtra(EXTRA_ICE_CANDIDATES, iceCandidateParcels);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendHangup(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast, @NonNull CallManager.HangupType hangupType, @NonNull Integer deviceId, @NonNull Boolean useLegacyHangupMessage) {
    Log.i(TAG, "onSendHangup: id: " + callId.format(remoteDevice) + " type: " + hangupType.name() + " isLegacy: " + useLegacyHangupMessage);

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_HANGUP)
            .putExtra(EXTRA_CALL_ID,          callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,      remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE,    remoteDevice)
            .putExtra(EXTRA_BROADCAST,        broadcast)
            .putExtra(EXTRA_HANGUP_DEVICE_ID, deviceId.intValue())
            .putExtra(EXTRA_HANGUP_IS_LEGACY, useLegacyHangupMessage.booleanValue())
            .putExtra(EXTRA_HANGUP_TYPE,      getHangupTypeFromCallHangupType(hangupType).getCode());

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendBusy(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast) {
    Log.i(TAG, "onSendBusy: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_BUSY)
            .putExtra(EXTRA_CALL_ID,       callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,   remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE, remoteDevice)
            .putExtra(EXTRA_BROADCAST,     broadcast);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendCallMessage(@NonNull UUID uuid, @NonNull byte[] opaque) {
    Log.i(TAG, "onSendCallMessage:");

    Intent intent = new Intent(this, WebRtcCallService.class);

    intent.setAction(ACTION_SEND_OPAQUE_MESSAGE)
          .putExtra(EXTRA_UUID, uuid.toString())
          .putExtra(EXTRA_OPAQUE_MESSAGE, opaque);

    startService(intent);
  }

  @Override
  public void onSendHttpRequest(long requestId, @NonNull String url, @NonNull CallManager.HttpMethod httpMethod, @Nullable List<HttpHeader> headers, @Nullable byte[] body) {
    Log.i(TAG, "onSendHttpRequest(): request_id: " + requestId);
    networkExecutor.execute(() -> {
      List<Pair<String, String>> headerPairs;
      if (headers != null) {
        headerPairs = Stream.of(headers)
                            .map(header -> new Pair<>(header.getName(), header.getValue()))
                            .toList();
      } else {
        headerPairs = Collections.emptyList();
      }

      CallingResponse response = messageSender.makeCallingRequest(requestId, url, httpMethod.name(), headerPairs, body);

      Intent intent = new Intent(this, WebRtcCallService.class);

      if (response instanceof CallingResponse.Success) {
        CallingResponse.Success success = (CallingResponse.Success) response;

        intent.setAction(ACTION_HTTP_SUCCESS)
              .putExtra(EXTRA_HTTP_REQUEST_ID, success.getRequestId())
              .putExtra(EXTRA_HTTP_RESPONSE_STATUS, success.getResponseStatus())
              .putExtra(EXTRA_HTTP_RESPONSE_BODY, success.getResponseBody());
      } else {
        intent.setAction(ACTION_HTTP_FAILURE)
              .putExtra(EXTRA_HTTP_REQUEST_ID, response.getRequestId());
      }

      startService(intent);
    });
  }

  @Override
  public void requestMembershipProof(@NonNull GroupCall groupCall) {
    Log.i(TAG, "requestMembershipProof():");

    networkExecutor.execute(() -> {
      try {
        GroupExternalCredential credential = GroupManager.getGroupExternalCredential(this, serviceState.getCallInfoState().getCallRecipient().getGroupId().get().requireV2());

        Intent intent = new Intent(this, WebRtcCallService.class);

        intent.setAction(ACTION_GROUP_REQUEST_MEMBERSHIP_PROOF)
              .putExtra(EXTRA_GROUP_EXTERNAL_TOKEN, credential.getTokenBytes().toByteArray())
              .putExtra(EXTRA_GROUP_CALL_HASH, groupCall.hashCode());

        startService(intent);
      } catch (IOException e) {
        Log.w(TAG, "Unable to get group membership proof from service", e);
        onEnded(groupCall, GroupCall.GroupCallEndReason.SFU_CLIENT_FAILED_TO_JOIN);
      } catch (VerificationFailedException e) {
        Log.w(TAG, "Unable to verify group membership proof", e);
        onEnded(groupCall, GroupCall.GroupCallEndReason.DEVICE_EXPLICITLY_DISCONNECTED);
      }
    });
  }

  @Override
  public void requestGroupMembers(@NonNull GroupCall groupCall) {
    startService(new Intent(this, WebRtcCallService.class).setAction(ACTION_GROUP_REQUEST_UPDATE_MEMBERS));
  }

  @Override
  public void onLocalDeviceStateChanged(@NonNull GroupCall groupCall) {
    startService(new Intent(this, WebRtcCallService.class).setAction(ACTION_GROUP_LOCAL_DEVICE_STATE_CHANGED));
  }

  @Override
  public void onRemoteDeviceStatesChanged(@NonNull GroupCall groupCall) {
    startService(new Intent(this, WebRtcCallService.class).setAction(ACTION_GROUP_REMOTE_DEVICE_STATE_CHANGED));
  }

  @Override
  public void onPeekChanged(@NonNull GroupCall groupCall) {
    startService(new Intent(this, WebRtcCallService.class).setAction(ACTION_GROUP_JOINED_MEMBERSHIP_CHANGED));
  }

  @Override
  public void onEnded(@NonNull GroupCall groupCall, @NonNull GroupCall.GroupCallEndReason groupCallEndReason) {
    Intent intent = new Intent(this, WebRtcCallService.class);

    intent.setAction(ACTION_GROUP_CALL_ENDED)
          .putExtra(EXTRA_GROUP_CALL_HASH, groupCall.hashCode())
          .putExtra(EXTRA_GROUP_CALL_END_REASON, groupCallEndReason.ordinal());

    startService(intent);
  }
}
