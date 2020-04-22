package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ResultReceiver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Pair;

import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.CallManager.CallEvent;
import org.signal.ringrtc.Remote;

import org.greenrobot.eventbus.EventBus;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase.VibrateState;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.notifications.DoNotDisturbUtil;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.CallState;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.IceCandidateParcel;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TelephonyUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder;
import org.thoughtcrime.securesms.webrtc.IncomingPstnCallReceiver;
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager;
import org.thoughtcrime.securesms.webrtc.audio.BluetoothStateManager;
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.lang.Thread;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_ESTABLISHED;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_CONNECTING;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;
import static org.thoughtcrime.securesms.webrtc.CallNotificationBuilder.TYPE_OUTGOING_RINGING;

public class WebRtcCallService extends Service implements CallManager.Observer,
                                                          BluetoothStateManager.BluetoothStateListener,
                                                          CameraEventListener
{

  private static final String TAG = WebRtcCallService.class.getSimpleName();

  public static final String EXTRA_MUTE               = "mute_value";
  public static final String EXTRA_AVAILABLE          = "enabled_value";
  public static final String EXTRA_TIMESTAMP          = "timestamp";
  public static final String EXTRA_CALL_ID            = "call_id";
  public static final String EXTRA_RESULT_RECEIVER    = "result_receiver";
  public static final String EXTRA_SPEAKER            = "audio_speaker";
  public static final String EXTRA_BLUETOOTH          = "audio_bluetooth";
  public static final String EXTRA_REMOTE_PEER        = "remote_peer";
  public static final String EXTRA_REMOTE_DEVICE      = "remote_device";
  public static final String EXTRA_OFFER_DESCRIPTION  = "offer_description";
  public static final String EXTRA_ANSWER_DESCRIPTION = "answer_description";
  public static final String EXTRA_ICE_CANDIDATES     = "ice_candidates";
  public static final String EXTRA_ENABLE             = "enable_value";
  public static final String EXTRA_BROADCAST          = "broadcast";

  public static final String ACTION_OUTGOING_CALL               = "CALL_OUTGOING";
  public static final String ACTION_DENY_CALL                   = "DENY_CALL";
  public static final String ACTION_LOCAL_HANGUP                = "LOCAL_HANGUP";
  public static final String ACTION_SET_MUTE_AUDIO              = "SET_MUTE_AUDIO";
  public static final String ACTION_FLIP_CAMERA                 = "FLIP_CAMERA";
  public static final String ACTION_BLUETOOTH_CHANGE            = "BLUETOOTH_CHANGE";
  public static final String ACTION_WIRED_HEADSET_CHANGE        = "WIRED_HEADSET_CHANGE";
  public static final String ACTION_SCREEN_OFF                  = "SCREEN_OFF";
  public static final String ACTION_IS_IN_CALL_QUERY            = "IS_IN_CALL";
  public static final String ACTION_SET_AUDIO_SPEAKER           = "SET_AUDIO_SPEAKER";
  public static final String ACTION_SET_AUDIO_BLUETOOTH         = "SET_AUDIO_BLUETOOTH";
  public static final String ACTION_CALL_CONNECTED              = "CALL_CONNECTED";
  public static final String ACTION_START_OUTGOING_CALL         = "START_OUTGOING_CALL";
  public static final String ACTION_START_INCOMING_CALL         = "START_INCOMING_CALL";
  public static final String ACTION_LOCAL_RINGING               = "LOCAL_RINGING";
  public static final String ACTION_REMOTE_RINGING              = "REMOTE_RINGING";
  public static final String ACTION_ACCEPT_CALL                 = "ACCEPT_CALL";
  public static final String ACTION_SEND_OFFER                  = "SEND_OFFER";
  public static final String ACTION_SEND_ANSWER                 = "SEND_ANSWER";
  public static final String ACTION_SEND_ICE_CANDIDATES         = "SEND_ICE_CANDIDATES";
  public static final String ACTION_SEND_HANGUP                 = "SEND_HANGUP";
  public static final String ACTION_SEND_BUSY                   = "SEND_BUSY";
  public static final String ACTION_RECEIVE_OFFER               = "RECEIVE_OFFER";
  public static final String ACTION_RECEIVE_ANSWER              = "RECEIVE_ANSWER";
  public static final String ACTION_RECEIVE_ICE_CANDIDATES      = "RECEIVE_ICE_CANDIDATES";
  public static final String ACTION_RECEIVE_HANGUP              = "RECEIVE_HANGUP";
  public static final String ACTION_RECEIVE_BUSY                = "RECEIVE_BUSY";
  public static final String ACTION_REMOTE_VIDEO_ENABLE         = "REMOTE_VIDEO_ENABLE";
  public static final String ACTION_SET_ENABLE_VIDEO            = "SET_ENABLE_VIDEO";
  public static final String ACTION_ENDED_REMOTE_HANGUP         = "ENDED_REMOTE_HANGUP";
  public static final String ACTION_ENDED_REMOTE_BUSY           = "ENDED_REMOTE_BUSY";
  public static final String ACTION_ENDED_REMOTE_GLARE          = "ENDED_REMOTE_GLARE";
  public static final String ACTION_ENDED_TIMEOUT               = "ENDED_TIMEOUT";
  public static final String ACTION_ENDED_INTERNAL_FAILURE      = "ENDED_INTERNAL_FAILURE";
  public static final String ACTION_ENDED_SIGNALING_FAILURE     = "ENDED_SIGNALING_FAILURE";
  public static final String ACTION_ENDED_CONNECTION_FAILURE    = "ENDED_CONNECTION_FAILURE";
  public static final String ACTION_ENDED_RX_OFFER_EXPIRED      = "ENDED_RX_OFFER_EXPIRED";
  public static final String ACTION_ENDED_RX_OFFER_WHILE_ACTIVE = "ENDED_RX_OFFER_WHILE_ACTIVE";
  public static final String ACTION_CALL_CONCLUDED              = "CALL_CONCLUDED";

  private CameraState localCameraState    = CameraState.UNKNOWN;
  private boolean     microphoneEnabled   = true;
  private boolean     remoteVideoEnabled  = false;
  private boolean     bluetoothAvailable  = false;
  private boolean     enableVideoOnCreate = false;

  private SignalServiceMessageSender      messageSender;
  private SignalServiceAccountManager     accountManager;
  private SignalAudioManager              audioManager;
  private BluetoothStateManager           bluetoothStateManager;
  private WiredHeadsetStateReceiver       wiredHeadsetStateReceiver;
  private PowerButtonReceiver             powerButtonReceiver;
  private LockManager                     lockManager;
  private IncomingPstnCallReceiver        callReceiver;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  @Nullable private CallManager         callManager;
  @Nullable private RemotePeer          activePeer;
  @Nullable private SurfaceViewRenderer localRenderer;
  @Nullable private SurfaceViewRenderer remoteRenderer;
  @Nullable private EglBase             eglBase;
  @Nullable private Camera              camera;

  private final ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
  private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

  private final PhoneStateListener hangUpRtcOnDeviceCallAnswered = new HangUpRtcOnPstnCallAnsweredListener();

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "onCreate");

    initializeResources();

    registerIncomingPstnCallReceiver();
    registerUncaughtExceptionHandler();
    registerWiredHeadsetStateReceiver();

    TelephonyUtil.getManager(this)
                 .listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_CALL_STATE);
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.i(TAG, "onStartCommand...");
    if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

    serviceExecutor.execute(() -> {
      if      (intent.getAction().equals(ACTION_RECEIVE_OFFER))               handleReceivedOffer(intent);
      else if (intent.getAction().equals(ACTION_RECEIVE_BUSY))                handleReceivedBusy(intent);
      else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle())   handleOutgoingCall(intent);
      else if (intent.getAction().equals(ACTION_DENY_CALL))                   handleDenyCall(intent);
      else if (intent.getAction().equals(ACTION_LOCAL_HANGUP))                handleLocalHangup(intent);
      else if (intent.getAction().equals(ACTION_SET_MUTE_AUDIO))              handleSetMuteAudio(intent);
      else if (intent.getAction().equals(ACTION_FLIP_CAMERA))                 handleSetCameraFlip(intent);
      else if (intent.getAction().equals(ACTION_BLUETOOTH_CHANGE))            handleBluetoothChange(intent);
      else if (intent.getAction().equals(ACTION_WIRED_HEADSET_CHANGE))        handleWiredHeadsetChange(intent);
      else if (intent.getAction().equals(ACTION_SCREEN_OFF))                  handleScreenOffChange(intent);
      else if (intent.getAction().equals(ACTION_CALL_CONNECTED))              handleCallConnected(intent);
      else if (intent.getAction().equals(ACTION_IS_IN_CALL_QUERY))            handleIsInCallQuery(intent);
      else if (intent.getAction().equals(ACTION_SET_AUDIO_SPEAKER))           handleSetSpeakerAudio(intent);
      else if (intent.getAction().equals(ACTION_SET_AUDIO_BLUETOOTH))         handleSetBluetoothAudio(intent);
      else if (intent.getAction().equals(ACTION_START_OUTGOING_CALL))         handleStartOutgoingCall(intent);
      else if (intent.getAction().equals(ACTION_START_INCOMING_CALL))         handleStartIncomingCall(intent);
      else if (intent.getAction().equals(ACTION_ACCEPT_CALL))                 handleAcceptCall(intent);
      else if (intent.getAction().equals(ACTION_LOCAL_RINGING))               handleLocalRinging(intent);
      else if (intent.getAction().equals(ACTION_REMOTE_RINGING))              handleRemoteRinging(intent);
      else if (intent.getAction().equals(ACTION_SEND_OFFER))                  handleSendOffer(intent);
      else if (intent.getAction().equals(ACTION_SEND_ANSWER))                 handleSendAnswer(intent);
      else if (intent.getAction().equals(ACTION_SEND_ICE_CANDIDATES))         handleSendIceCandidates(intent);
      else if (intent.getAction().equals(ACTION_SEND_HANGUP))                 handleSendHangup(intent);
      else if (intent.getAction().equals(ACTION_SEND_BUSY))                   handleSendBusy(intent);
      else if (intent.getAction().equals(ACTION_RECEIVE_ANSWER))              handleReceivedAnswer(intent);
      else if (intent.getAction().equals(ACTION_RECEIVE_ICE_CANDIDATES))      handleReceivedIceCandidates(intent);
      else if (intent.getAction().equals(ACTION_RECEIVE_HANGUP))              handleReceivedHangup(intent);
      else if (intent.getAction().equals(ACTION_REMOTE_VIDEO_ENABLE))         handleRemoteVideoEnable(intent);
      else if (intent.getAction().equals(ACTION_SET_ENABLE_VIDEO))            handleSetEnableVideo(intent);
      else if (intent.getAction().equals(ACTION_ENDED_REMOTE_HANGUP))         handleEndedRemoteHangup(intent);
      else if (intent.getAction().equals(ACTION_ENDED_REMOTE_BUSY))           handleEndedRemoteBusy(intent);
      else if (intent.getAction().equals(ACTION_ENDED_REMOTE_GLARE))          handleEndedRemoteGlare(intent);
      else if (intent.getAction().equals(ACTION_ENDED_TIMEOUT))               handleEndedTimeout(intent);
      else if (intent.getAction().equals(ACTION_ENDED_INTERNAL_FAILURE))      handleEndedInternalFailure(intent);
      else if (intent.getAction().equals(ACTION_ENDED_SIGNALING_FAILURE))     handleEndedSignalingFailure(intent);
      else if (intent.getAction().equals(ACTION_ENDED_CONNECTION_FAILURE))    handleEndedConnectionFailure(intent);
      else if (intent.getAction().equals(ACTION_ENDED_RX_OFFER_EXPIRED))      handleEndedReceivedOfferExpired(intent);
      else if (intent.getAction().equals(ACTION_ENDED_RX_OFFER_WHILE_ACTIVE)) handleEndedReceivedOfferWhileActive(intent);
      else if (intent.getAction().equals(ACTION_CALL_CONCLUDED))              handleCallConcluded(intent);

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

    if (callReceiver != null) {
      unregisterReceiver(callReceiver);
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
    localCameraState = newCameraState;
    if (activePeer != null) {
      sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }


  // Initializers

  private void initializeResources() {
    this.messageSender         = ApplicationDependencies.getSignalServiceMessageSender();
    this.accountManager        = ApplicationDependencies.getSignalServiceAccountManager();
    this.lockManager           = new LockManager(this);
    this.audioManager          = new SignalAudioManager(this);
    this.bluetoothStateManager = new BluetoothStateManager(this, this);

    this.messageSender.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));
    this.accountManager.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));

    try {
      this.callManager = CallManager.createCallManager(this);
    } catch  (CallException e) {
      callFailure("Unable to create Call Manager: ", e);
    }

  }

  private void registerIncomingPstnCallReceiver() {
    callReceiver = new IncomingPstnCallReceiver();
    registerReceiver(callReceiver, new IntentFilter("android.intent.action.PHONE_STATE"));
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

  private void registerPowerButtonReceiver() {
    if (powerButtonReceiver == null) {
      powerButtonReceiver = new PowerButtonReceiver();

      registerReceiver(powerButtonReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
  }

  private void unregisterPowerButtonReceiver() {
    if (powerButtonReceiver != null) {
      unregisterReceiver(powerButtonReceiver);

      powerButtonReceiver = null;
    }
  }

  // Handlers

  private void handleReceivedOffer(Intent intent) {
    CallId     callId       = getCallId(intent);
    RemotePeer remotePeer   = getRemotePeer(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    String     offer        = intent.getStringExtra(EXTRA_OFFER_DESCRIPTION);
    Long       timeStamp    = intent.getLongExtra(EXTRA_TIMESTAMP, -1);

    Log.i(TAG, "handleReceivedOffer(): id: " + callId.format(remoteDevice));

    if (TelephonyUtil.isAnyPstnLineBusy(this)) {
      Log.i(TAG, "handleReceivedOffer(): PSTN line is busy.");
      intent.putExtra(EXTRA_BROADCAST, true);
      handleSendBusy(intent);
      insertMissedCall(remotePeer, true);
      return;
    }

    try {
      callManager.receivedOffer(callId, remotePeer, remoteDevice, offer, timeStamp);
    } catch  (CallException e) {
      callFailure("Unable to process received offer: ", e);
    }
  }

  private void handleOutgoingCall(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    if (remotePeer.getState() != CallState.IDLE) {
      throw new IllegalStateException("Dialing from non-idle?");
    }

    Log.i(TAG, "handleOutgoingCall():");

    initializeVideo();

    try {
      callManager.call(remotePeer);
    } catch  (CallException e) {
      callFailure("Unable to create outgoing call: ", e);
    }
  }

  private void handleIsInCallQuery(Intent intent) {
    ResultReceiver resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);

    if (resultReceiver != null) {
      resultReceiver.send(activePeer != null ? 1 : 0, null);
    }
  }

  private void insertMissedCall(@NonNull RemotePeer remotePeer, boolean signal) {
    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getSmsDatabase(this).insertMissedCall(remotePeer.getId());
    MessageNotifier.updateNotification(this, messageAndThreadId.second, signal);
  }

  private void handleDenyCall(Intent intent) {
    if (activePeer == null) {
      Log.i(TAG, "handleDenyCall(): Ignoring for inactive call.");
      return;
    }

    if (activePeer.getState() != CallState.LOCAL_RINGING) {
      Log.w(TAG, "Can only deny from ringing!");
      return;
    }

    Log.i(TAG, "handleDenyCall():");

    try {
      callManager.hangup();
      DatabaseFactory.getSmsDatabase(this).insertMissedCall(activePeer.getId());
    } catch  (CallException e) {
      callFailure("hangup() failed: ", e);
    }
  }

  private void handleSetSpeakerAudio(Intent intent) {
    boolean      isSpeaker    = intent.getBooleanExtra(EXTRA_SPEAKER, false);
    AudioManager audioManager = ServiceUtil.getAudioManager(this);

    audioManager.setSpeakerphoneOn(isSpeaker);

    if (isSpeaker && audioManager.isBluetoothScoOn()) {
      audioManager.stopBluetoothSco();
      audioManager.setBluetoothScoOn(false);
    }

    if (!localCameraState.isEnabled()) {
      lockManager.updatePhoneState(getInCallPhoneState());
    }

    if (activePeer != null) {
      sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleSetBluetoothAudio(Intent intent) {
    boolean      isBluetooth  = intent.getBooleanExtra(EXTRA_BLUETOOTH, false);
    AudioManager audioManager = ServiceUtil.getAudioManager(this);

    if (isBluetooth) {
      audioManager.startBluetoothSco();
      audioManager.setBluetoothScoOn(true);
    } else {
      audioManager.stopBluetoothSco();
      audioManager.setBluetoothScoOn(false);
    }

    if (!localCameraState.isEnabled()) {
      lockManager.updatePhoneState(getInCallPhoneState());
    }

    if (activePeer != null) {
      sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleSetMuteAudio(Intent intent) {
    boolean muted     = intent.getBooleanExtra(EXTRA_MUTE, false);
    microphoneEnabled = !muted;

    if (activePeer == null) {
      Log.w(TAG, "handleSetMuteAudio(): Ignoring for inactive call.");
      return;
    }

    if (activePeer.getState() == CallState.CONNECTED) {
      try {
        callManager.setAudioEnable(microphoneEnabled);
      } catch (CallException e) {
        callFailure("Enabling audio failed: ", e);
      }
    }

    if (activePeer != null) {
      sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleSetCameraFlip(Intent intent) {
    Log.i(TAG, "handleSetCameraFlip()...");

    if (localCameraState.isEnabled() && camera != null) {
      camera.flip();
      localCameraState = camera.getCameraState();
      if (activePeer != null) {
        sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }
    }
  }

  private void handleBluetoothChange(Intent intent) {
    bluetoothAvailable = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

    if (activePeer != null) {
      sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleWiredHeadsetChange(Intent intent) {
    Log.i(TAG, "handleWiredHeadsetChange...");

    if ((activePeer != null) &&
        (activePeer.getState() == CallState.CONNECTED     ||
         activePeer.getState() == CallState.DIALING       ||
         activePeer.getState() == CallState.RECEIVED_BUSY ||
         activePeer.getState() == CallState.REMOTE_RINGING))
    {
      AudioManager audioManager = ServiceUtil.getAudioManager(this);
      boolean      present      = intent.getBooleanExtra(EXTRA_AVAILABLE, false);

      if (present && audioManager.isSpeakerphoneOn()) {
        audioManager.setSpeakerphoneOn(false);
        audioManager.setBluetoothScoOn(false);
      } else if (!present && !audioManager.isSpeakerphoneOn() && !audioManager.isBluetoothScoOn() && localCameraState.isEnabled()) {
        audioManager.setSpeakerphoneOn(true);
      }

      sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }
  }

  private void handleScreenOffChange(Intent intent) {
    if ((activePeer != null) &&
        (activePeer.getState() == CallState.ANSWERING ||
         activePeer.getState() == CallState.LOCAL_RINGING))
    {
      Log.i(TAG, "Silencing incoming ringer...");
      audioManager.silenceIncomingRinger();
    }
  }

  private void handleStartOutgoingCall(Intent intent) {
    Log.i(TAG, "handleStartOutgoingCall(): callId: " + activePeer.getCallId());

    sendMessage(WebRtcViewModel.State.CALL_OUTGOING, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    lockManager.updatePhoneState(getInCallPhoneState());
    audioManager.initializeAudioForCall();
    audioManager.startOutgoingRinger(OutgoingRinger.Type.RINGING);
    bluetoothStateManager.setWantsConnection(true);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setCallInProgressNotification(TYPE_OUTGOING_RINGING, activePeer);
    }
    DatabaseFactory.getSmsDatabase(this).insertOutgoingCall(activePeer.getId());

    retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.activePeer.getState(), this.activePeer.getCallId()) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> iceServers) {

          boolean isAlwaysTurn = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);

          LinkedList<Integer> deviceList = new LinkedList<Integer>();
          deviceList.add(SignalServiceAddress.DEFAULT_DEVICE_ID);

          try {
            callManager.proceed(activePeer.getCallId(),
                                WebRtcCallService.this,
                                eglBase,
                                localRenderer,
                                remoteRenderer,
                                camera,
                                iceServers,
                                isAlwaysTurn,
                                deviceList,
                                enableVideoOnCreate);
          } catch  (CallException e) {
            callFailure("Unable to proceed with call: ", e);
          }

          localCameraState = camera.getCameraState();
          if (activePeer != null) {
            sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
          }
        }
      });
  }

  private void handleStartIncomingCall(Intent intent) {
    if (activePeer.getState() != CallState.ANSWERING) {
      throw new IllegalStateException("StartIncoming while non-ANSWERING");
    }

    Log.i(TAG, "handleStartIncomingCall(): callId: " + activePeer.getCallId());

    initializeVideo();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setCallInProgressNotification(TYPE_INCOMING_CONNECTING, activePeer);
    }
    retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.activePeer.getState(), this.activePeer.getCallId()) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> iceServers) {

          boolean isAlwaysTurn = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);
          boolean hideIp       = !activePeer.getRecipient().isSystemContact() || isAlwaysTurn;

          LinkedList<Integer> deviceList = new LinkedList<Integer>();

          try {
            callManager.proceed(activePeer.getCallId(),
                                WebRtcCallService.this,
                                eglBase,
                                localRenderer,
                                remoteRenderer,
                                camera,
                                iceServers,
                                hideIp,
                                deviceList,
                                false);
          } catch  (CallException e) {
            callFailure("Unable to proceed with call: ", e);
          }

          lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
          if (activePeer != null) {
            sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
          }
        }
      });
  }

  private void handleAcceptCall(Intent intent) {

    if (activePeer != null && activePeer.getState() != CallState.LOCAL_RINGING) {
      Log.w(TAG, "handleAcceptCall(): Ignoring for inactive call.");
      return;
    }

    Log.i(TAG, "handleAcceptCall(): call_id: " + activePeer.getCallId());

    DatabaseFactory.getSmsDatabase(this).insertReceivedCall(activePeer.getId());

    try {
      callManager.acceptCall(activePeer.getCallId());
    } catch (CallException e) {
      callFailure("accept() failed: ", e);
    }
  }

  private void handleSendOffer(Intent intent) {
    RemotePeer remotePeer   = getRemotePeer(intent);
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    Boolean    broadcast    = intent.getBooleanExtra(EXTRA_BROADCAST, false);
    String     offer        = intent.getStringExtra(EXTRA_OFFER_DESCRIPTION);

    Log.i(TAG, "handleSendOffer: id: " + callId.format(remoteDevice));

    OfferMessage             offerMessage = new OfferMessage(callId.longValue(), offer);
    SignalServiceCallMessage callMessage  = SignalServiceCallMessage.forOffer(offerMessage);

    sendCallMessage(remotePeer, remoteDevice, broadcast, callMessage);
  }

  private void handleSendAnswer(Intent intent) {
    RemotePeer remotePeer   = getRemotePeer(intent);
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    Boolean    broadcast    = intent.getBooleanExtra(EXTRA_BROADCAST, false);
    String     answer       = intent.getStringExtra(EXTRA_ANSWER_DESCRIPTION);

    Log.i(TAG, "handleSendAnswer: id: " + callId.format(remoteDevice));

    AnswerMessage            answerMessage = new AnswerMessage(callId.longValue(), answer);
    SignalServiceCallMessage callMessage   = SignalServiceCallMessage.forAnswer(answerMessage);

    sendCallMessage(remotePeer, remoteDevice, broadcast, callMessage);
  }

  private void handleSendIceCandidates(Intent intent) {
    RemotePeer remotePeer   = getRemotePeer(intent);
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    Boolean    broadcast    = intent.getBooleanExtra(EXTRA_BROADCAST, false);
    ArrayList<IceCandidateParcel> iceCandidates = intent.getParcelableArrayListExtra(EXTRA_ICE_CANDIDATES);

    Log.i(TAG, "handleSendIceCandidates: id: " + callId.format(remoteDevice));

    LinkedList<IceUpdateMessage> iceUpdateMessages = new LinkedList();
    for (IceCandidateParcel parcel : iceCandidates) {
      iceUpdateMessages.add(parcel.getIceUpdateMessage(callId));
    }

    SignalServiceCallMessage callMessage = SignalServiceCallMessage.forIceUpdates(iceUpdateMessages);

    sendCallMessage(remotePeer, remoteDevice, broadcast, callMessage);
  }

  private void handleSendHangup(Intent intent) {
    RemotePeer remotePeer   = getRemotePeer(intent);
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    Boolean    broadcast    = intent.getBooleanExtra(EXTRA_BROADCAST, false);

    Log.i(TAG, "handleSendHangup: id: " + callId.format(remoteDevice));

    HangupMessage             hangupMessage = new HangupMessage(callId.longValue());
    SignalServiceCallMessage  callMessage   = SignalServiceCallMessage.forHangup(hangupMessage);

    sendCallMessage(remotePeer, remoteDevice, broadcast, callMessage);
  }

  private void handleSendBusy(Intent intent) {
    RemotePeer remotePeer   = getRemotePeer(intent);
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    Boolean    broadcast    = intent.getBooleanExtra(EXTRA_BROADCAST, false);

    Log.i(TAG, "handleSendBusy: id: " + callId.format(remoteDevice));

    BusyMessage              busyMessage = new BusyMessage(callId.longValue());
    SignalServiceCallMessage callMessage = SignalServiceCallMessage.forBusy(busyMessage);

    sendCallMessage(remotePeer, remoteDevice, broadcast, callMessage);
  }

  private void handleReceivedAnswer(Intent intent) {
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    String     description  = intent.getStringExtra(EXTRA_ANSWER_DESCRIPTION);

    Log.i(TAG, "handleReceivedAnswer(): id: " + callId.format(remoteDevice));

    try {
      callManager.receivedAnswer(callId, remoteDevice, description);
    } catch  (CallException e) {
      callFailure("receivedAnswer() failed: ", e);
    }
  }

  private void handleReceivedIceCandidates(Intent intent) {
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);
    ArrayList<IceCandidateParcel> iceCandidateParcels = intent.getParcelableArrayListExtra(EXTRA_ICE_CANDIDATES);

    Log.i(TAG, "handleReceivedIceCandidates: id: " + callId.format(remoteDevice) + ", count: " + iceCandidateParcels.size());

    LinkedList<IceCandidate> iceCandidates = new LinkedList();
    for (IceCandidateParcel parcel : iceCandidateParcels) {
      iceCandidates.add(parcel.getIceCandidate());
    }

    try {
      callManager.receivedIceCandidates(callId, remoteDevice, iceCandidates);
    } catch  (CallException e) {
      callFailure("receivedIceCandidates() failed: ", e);
    }
  }

  private void handleReceivedHangup(Intent intent) {
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);

    Log.i(TAG, "handleReceivedHangup(): id: " + callId.format(remoteDevice));

    try {
      callManager.receivedHangup(callId, remoteDevice);
    } catch  (CallException e) {
      callFailure("receivedHangup() failed: ", e);
    }
  }

  private void handleReceivedBusy(Intent intent) {
    CallId     callId       = getCallId(intent);
    Integer    remoteDevice = intent.getIntExtra(EXTRA_REMOTE_DEVICE, -1);

    Log.i(TAG, "handleReceivedBusy(): id: " + callId.format(remoteDevice));

    try {
      callManager.receivedBusy(callId, remoteDevice);
    } catch  (CallException e) {
      callFailure("receivedBusy() failed: ", e);
    }
  }

  private void handleLocalRinging(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);
    Recipient  recipient  = remotePeer.getRecipient();

    Log.i(TAG, "handleLocalRinging(): call_id: " + remotePeer.getCallId());

    if (!remotePeer.callIdEquals(activePeer)) {
      Log.w(TAG, "handleLocalRinging(): Ignoring for inactive call.");
      return;
    }

    activePeer.localRinging();
    lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

    sendMessage(WebRtcViewModel.State.CALL_INCOMING, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    boolean shouldDisturbUserWithCall = DoNotDisturbUtil.shouldDisturbUserWithCall(getApplicationContext(), recipient);
    if (shouldDisturbUserWithCall) {
      startCallCardActivityIfPossible();
    }

    audioManager.initializeAudioForCall();
    if (shouldDisturbUserWithCall && TextSecurePreferences.isCallNotificationsEnabled(this)) {
      Uri          ringtone     = recipient.resolve().getCallRingtone();
      VibrateState vibrateState = recipient.resolve().getCallVibrate();

      if (ringtone == null) ringtone = TextSecurePreferences.getCallNotificationRingtone(this);

      audioManager.startIncomingRinger(ringtone, vibrateState == VibrateState.ENABLED || (vibrateState == VibrateState.DEFAULT && TextSecurePreferences.isCallNotificationVibrateEnabled(this)));
    }

    registerPowerButtonReceiver();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setCallInProgressNotification(TYPE_INCOMING_RINGING, activePeer);
    }
  }

  private void handleRemoteRinging(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);
    Recipient  recipient  = remotePeer.getRecipient();

    Log.i(TAG, "handleRemoteRinging(): call_id: " + remotePeer.getCallId());

    if (!remotePeer.callIdEquals(activePeer)) {
      Log.w(TAG, "handleRemoteRinging(): Ignoring for inactive call.");
      return;
    }

    activePeer.remoteRinging();
    sendMessage(WebRtcViewModel.State.CALL_RINGING, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void handleCallConnected(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleCallConnected: call_id: " + remotePeer.getCallId());

    if (!remotePeer.callIdEquals(activePeer)) {
      Log.w(TAG, "handleCallConnected(): Ignoring for inactive call.");
      return;
    }

    audioManager.startCommunication(activePeer.getState() == CallState.REMOTE_RINGING);
    bluetoothStateManager.setWantsConnection(true);

    activePeer.connected();

    if (localCameraState.isEnabled()) {
      lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
    } else {
      lockManager.updatePhoneState(getInCallPhoneState());
    }

    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

    unregisterPowerButtonReceiver();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      setCallInProgressNotification(TYPE_ESTABLISHED, activePeer);
    }

    try {
      callManager.setCommunicationMode();
      callManager.setAudioEnable(microphoneEnabled);
      callManager.setVideoEnable(localCameraState.isEnabled());
    } catch (CallException e) {
      callFailure("Enabling audio/video failed: ", e);
    }
  }

  private void handleRemoteVideoEnable(Intent intent) {
    Boolean enable = intent.getBooleanExtra(EXTRA_ENABLE, false);

    if (activePeer == null) {
      Log.w(TAG, "handleRemoteVideoEnable(): Ignoring for inactive call.");
      return;
    }

    Log.i(TAG, "handleRemoteVideoEnable: call_id: " + activePeer.getCallId());

    remoteVideoEnabled = enable;
    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

  }

  private void handleSetEnableVideo(Intent intent) {
    boolean      enable       = intent.getBooleanExtra(EXTRA_ENABLE, false);
    AudioManager audioManager = ServiceUtil.getAudioManager(this);

    if (activePeer == null) {
      Log.w(TAG, "handleSetEnableVideo(): Ignoring for inactive call.");
      return;
    }

    Log.i(TAG, "handleSetEnableVideo(): call_id: " + activePeer.getCallId());

    if (activePeer.getState() != CallState.CONNECTED) {
      enableVideoOnCreate = enable;
      return;
    }

    try {
      callManager.setVideoEnable(enable);
    } catch  (CallException e) {
      callFailure("setVideoEnable() failed: ", e);
      return;
    }

    localCameraState = camera.getCameraState();

    if (localCameraState.isEnabled()) {
      lockManager.updatePhoneState(LockManager.PhoneState.IN_VIDEO);
    } else {
      lockManager.updatePhoneState(getInCallPhoneState());
    }

    if (localCameraState.isEnabled() &&
        !audioManager.isSpeakerphoneOn() &&
        !audioManager.isBluetoothScoOn() &&
        !audioManager.isWiredHeadsetOn())
    {
      audioManager.setSpeakerphoneOn(true);
    }

    sendMessage(viewModelStateFor(activePeer), activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
  }

  private void handleLocalHangup(Intent intent) {
    if (activePeer == null) {
      Log.w(TAG, "handleLocalHangup(): Ignoring for inactive call.");
      return;
    }

    Log.i(TAG, "handleLocalHangup(): call_id: " + activePeer.getCallId());

    if (activePeer.getState() == CallState.RECEIVED_BUSY) {
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      terminate();
    } else {
      accountManager.cancelInFlightRequests();
      messageSender.cancelInFlightRequests();

      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

      try {
        callManager.hangup();
      } catch  (CallException e) {
        callFailure("hangup() failed: ", e);
      }
    }
  }

  private void handleEndedReceivedOfferExpired(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleEndedReceivedOfferExpired(): call_id: " + remotePeer.getCallId());
    insertMissedCall(remotePeer, true);
  }

  private void handleEndedReceivedOfferWhileActive(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleEndedReceivedOfferWhileActive(): call_id: " + remotePeer.getCallId());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      switch (activePeer.getState()) {
        case DIALING:
        case REMOTE_RINGING: setCallInProgressNotification(TYPE_OUTGOING_RINGING,    activePeer); break;
        case IDLE:           setCallInProgressNotification(TYPE_INCOMING_CONNECTING, activePeer); break;
        case ANSWERING:      setCallInProgressNotification(TYPE_INCOMING_CONNECTING, activePeer); break;
        case LOCAL_RINGING:  setCallInProgressNotification(TYPE_INCOMING_RINGING,    activePeer); break;
        case CONNECTED:      setCallInProgressNotification(TYPE_ESTABLISHED,         activePeer); break;
        default:             throw new IllegalStateException();
      }
    }

    if (activePeer.getState() == CallState.IDLE) {
      stopForeground(true);
    }

    insertMissedCall(remotePeer, true);
  }

  private void handleEndedRemoteHangup(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleEndedRemoteHangup(): call_id: " + remotePeer.getCallId());

    if (remotePeer.callIdEquals(activePeer)) {
      boolean outgoingBeforeAccept = remotePeer.getState() == CallState.DIALING || remotePeer.getState() == CallState.REMOTE_RINGING;
      if (outgoingBeforeAccept) {
        sendMessage(WebRtcViewModel.State.RECIPIENT_UNAVAILABLE, remotePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      } else {
        sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, remotePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }
    }

    boolean incomingBeforeAccept = remotePeer.getState() == CallState.ANSWERING || remotePeer.getState() == CallState.LOCAL_RINGING;
    if (incomingBeforeAccept) {
      insertMissedCall(remotePeer, true);
    }
  }

  private void delayedBusyFinish(CallId callId) {
    if (activePeer != null && callId.equals(activePeer.getCallId())) {
      Log.i(TAG, "delayedBusyFinish(): calling terminate()");
      terminate();
    }
  }

  private void handleEndedRemoteBusy(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);
    CallId     callId     = remotePeer.getCallId();

    Log.i(TAG, "handleEndedRemoteBusy(): call_id: " + callId);

    if (!remotePeer.callIdEquals(activePeer)) {
      Log.w(TAG, "handleEndedRemoteBusy(): Ignoring for inactive call.");
      return;
    }

    activePeer.receivedBusy();
    sendMessage(WebRtcViewModel.State.CALL_BUSY, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);

    audioManager.startOutgoingRinger(OutgoingRinger.Type.BUSY);
    Util.runOnMainDelayed(() -> {
      delayedBusyFinish(callId);
    }, WebRtcCallActivity.BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleEndedRemoteGlare(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleEndedRemoteGlare(): call_id: " + remotePeer.getCallId());
    handleEndedRemoteBusy(intent);
  }

  private void handleEndedFailure(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleEndedFailure(): call_id: " + remotePeer.getCallId());
    if (remotePeer.callIdEquals(activePeer)) {
      sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }

    if (remotePeer.getState() == CallState.ANSWERING || remotePeer.getState() == CallState.LOCAL_RINGING) {
      insertMissedCall(remotePeer, true);
    }
  }

  private void handleEndedTimeout(Intent intent) {
    Log.i(TAG, "handleEndedTimeout():");

    handleEndedFailure(intent);
  }

  private void handleEndedInternalFailure(Intent intent) {
    Log.i(TAG, "handleEndedInternalFailure():");

    handleEndedFailure(intent);
  }

  private void handleEndedSignalingFailure(Intent intent) {
    Log.i(TAG, "handleEndedSignalingFailure():");

    handleEndedFailure(intent);
  }

  private void handleEndedConnectionFailure(Intent intent) {
    Log.i(TAG, "handleEndedConnectionFailure():");

    handleEndedFailure(intent);
  }

  private void handleCallConcluded(Intent intent) {
    RemotePeer remotePeer = getRemotePeer(intent);

    Log.i(TAG, "handleCallConcluded(): call_id: " + remotePeer.getCallId());
    if (!remotePeer.callIdEquals(activePeer)) {
      Log.w(TAG, "handleCallConcluded(): Ignoring for inactive call.");
      return;
    }

    boolean terminateAlreadyScheduled = activePeer.getState() == CallState.RECEIVED_BUSY;
    if (!terminateAlreadyScheduled) {
      terminate();
    }
  }

  /// Helper Methods

  private boolean isIdle() {
    return activePeer == null;
  }

  private void initializeVideo() {
    Util.runOnMainSync(() -> {

      eglBase        = EglBase.create();
      localRenderer  = new SurfaceViewRenderer(WebRtcCallService.this);
      remoteRenderer = new SurfaceViewRenderer(WebRtcCallService.this);

      localRenderer.init(eglBase.getEglBaseContext(), null);
      remoteRenderer.init(eglBase.getEglBaseContext(), null);

      camera           = new Camera(WebRtcCallService.this, WebRtcCallService.this, eglBase);
      localCameraState = camera.getCameraState();

    });
  }

  private void setCallInProgressNotification(int type, RemotePeer remotePeer) {
    startForeground(CallNotificationBuilder.getNotificationId(getApplicationContext(), type),
                    CallNotificationBuilder.getCallInProgressNotification(this, type, remotePeer.getRecipient()));
  }

  private synchronized void terminate() {
    Log.i(TAG, "terminate()");

    if (activePeer == null) {
      Log.i(TAG, "terminate(): skipping with no active peer");
      return;
    }

    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    stopForeground(true);
    boolean playDisconnectSound = (activePeer.getState() == CallState.DIALING)        ||
                                  (activePeer.getState() == CallState.REMOTE_RINGING) ||
                                  (activePeer.getState() == CallState.RECEIVED_BUSY)  ||
                                  (activePeer.getState() == CallState.CONNECTED);
    audioManager.stop(playDisconnectSound);
    bluetoothStateManager.setWantsConnection(false);

    if (camera != null) {
      camera.dispose();
      camera = null;
    }

    if (eglBase != null && localRenderer != null && remoteRenderer != null) {
      localRenderer.release();
      remoteRenderer.release();
      eglBase.release();

      localRenderer  = null;
      remoteRenderer = null;
      eglBase        = null;
    }

    this.localCameraState    = CameraState.UNKNOWN;
    this.activePeer          = null;
    this.microphoneEnabled   = true;
    this.remoteVideoEnabled  = false;
    this.enableVideoOnCreate = false;

    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull RemotePeer            remotePeer,
                           @NonNull CameraState           localCameraState,
                                    boolean               remoteVideoEnabled,
                                    boolean               bluetoothAvailable,
                                    boolean               microphoneEnabled)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state,
                                                         remotePeer.getRecipient(),
                                                         localCameraState,
                                                         localRenderer,
                                                         remoteRenderer,
                                                         remoteVideoEnabled,
                                                         bluetoothAvailable,
                                                         microphoneEnabled));
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull RemotePeer            remotePeer,
                           @NonNull IdentityKey           identityKey,
                           @NonNull CameraState           localCameraState,
                                    boolean               remoteVideoEnabled,
                                    boolean               bluetoothAvailable,
                                    boolean               microphoneEnabled)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state,
                                                         remotePeer.getRecipient(),
                                                         identityKey,
                                                         localCameraState,
                                                         localRenderer,
                                                         remoteRenderer,
                                                         remoteVideoEnabled,
                                                         bluetoothAvailable,
                                                         microphoneEnabled));
  }

  private ListenableFutureTask<Boolean> sendMessage(@NonNull final RemotePeer remotePeer,
                                                    @NonNull final SignalServiceCallMessage callMessage)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Recipient recipient = remotePeer.getRecipient();
        messageSender.sendCallMessage(RecipientUtil.toSignalServiceAddress(WebRtcCallService.this, recipient),
                                      UnidentifiedAccessUtil.getAccessFor(WebRtcCallService.this, recipient),
                                      callMessage);
        return true;
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private void startCallCardActivityIfPossible() {
    if (Build.VERSION.SDK_INT >= 29 && !ApplicationContext.getInstance(getApplicationContext()).isAppVisible()) {
      return;
    }

    Intent activityIntent = new Intent();
    activityIntent.setClass(this, WebRtcCallActivity.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    this.startActivity(activityIntent);
  }

  private @NonNull CallId getCallId(Intent intent) {
    return new CallId(intent.getLongExtra(EXTRA_CALL_ID, -1));
  }

  private @NonNull RemotePeer getRemotePeer(Intent intent) {
    RemotePeer remotePeer = intent.getParcelableExtra(EXTRA_REMOTE_PEER);
    if (remotePeer == null) throw new AssertionError("No RemotePeer in intent!");

    return remotePeer;
  }

  private void callFailure(String message, Throwable error) {
    Log.w(TAG, message, error);

    if (activePeer != null) {
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
    }

    if (callManager != null) {
      try {
        callManager.reset();
      } catch  (CallException e) {
        Log.w(TAG, "Unable to reset call manager: ", e);
      }
    } else {
        Log.w(TAG, "No call manager, not reseting.  Error message: " + message , error);
    }

    terminate();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private WebRtcViewModel.State viewModelStateFor(@NonNull RemotePeer remotePeer) {
    switch (remotePeer.getState()) {
      case CONNECTED:      return WebRtcViewModel.State.CALL_CONNECTED;
      case DIALING:        return WebRtcViewModel.State.CALL_OUTGOING;
      case REMOTE_RINGING: return WebRtcViewModel.State.CALL_RINGING;
      case LOCAL_RINGING:  return WebRtcViewModel.State.CALL_INCOMING;
      case ANSWERING:      return WebRtcViewModel.State.CALL_INCOMING;
      case IDLE:           return WebRtcViewModel.State.CALL_DISCONNECTED;
    }

    return WebRtcViewModel.State.CALL_DISCONNECTED;
  }

  private static class WiredHeadsetStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      int state = intent.getIntExtra("state", -1);

      Intent serviceIntent = new Intent(context, WebRtcCallService.class);
      serviceIntent.setAction(WebRtcCallService.ACTION_WIRED_HEADSET_CHANGE);
      serviceIntent.putExtra(WebRtcCallService.EXTRA_AVAILABLE, state != 0);
      context.startService(serviceIntent);
    }
  }

  private static class PowerButtonReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
        Intent serviceIntent = new Intent(context, WebRtcCallService.class);
        serviceIntent.setAction(WebRtcCallService.ACTION_SCREEN_OFF);
        context.startService(serviceIntent);
      }
    }
  }

  private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
    private final LockManager lockManager;

    private ProximityLockRelease(LockManager lockManager) {
      this.lockManager = lockManager;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
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

  private class HangUpRtcOnPstnCallAnsweredListener extends PhoneStateListener {
    @Override
    public void onCallStateChanged(int state, String phoneNumber) {
      super.onCallStateChanged(state, phoneNumber);
      if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
        hangup();
        Log.i(TAG, "Device phone call ended Signal call.");
      }
    }

    private void hangup() {
      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(ACTION_LOCAL_HANGUP);

      startService(intent);
    }
  }

  private ListenableFutureTask<List<PeerConnection.IceServer>> retrieveTurnServers() {
    Callable<List<PeerConnection.IceServer>> callable = () -> {
      LinkedList<PeerConnection.IceServer> results = new LinkedList<>();

      results.add(new PeerConnection.IceServer("stun:stun1.l.google.com:19302"));
      try {
        TurnServerInfo turnServerInfo = accountManager.getTurnServerInfo();

        for (String url : turnServerInfo.getUrls()) {
          Log.i(TAG, "ice_server: " + url);
          if (url.startsWith("turn")) {
            results.add(new PeerConnection.IceServer(url, turnServerInfo.getUsername(), turnServerInfo.getPassword()));
          } else {
            results.add(new PeerConnection.IceServer(url));
          }
        }
      } catch (IOException e) {
        Log.w(TAG, e);
      }

      return results;
    };

    ListenableFutureTask<List<PeerConnection.IceServer>> futureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(futureTask);

    return futureTask;
  }

  private abstract class StateAwareListener<V> implements FutureTaskListener<V> {

    private final CallState expectedState;
    private final CallId    expectedCallId;

    StateAwareListener(CallState expectedState, CallId expectedCallId) {
      this.expectedState  = expectedState;
      this.expectedCallId = expectedCallId;
    }

    public CallId getCallId() {
      return this.expectedCallId;
    }

    @Override
    public void onSuccess(V result) {
      if (!isConsistentState()) {
        Log.i(TAG, "State has changed since request, skipping success callback...");
        onStateChangeContinue();
      } else {
        onSuccessContinue(result);
      }
    }

    @Override
    public void onFailure(ExecutionException throwable) {
      if (!isConsistentState()) {
        Log.w(TAG, throwable);
        Log.w(TAG, "State has changed since request, skipping failure callback...");
        onStateChangeContinue();
      } else {
        onFailureContinue(throwable.getCause());
      }
    }

    public void onStateChangeContinue() {}

    private boolean isConsistentState() {
      return activePeer != null && expectedState == activePeer.getState() && expectedCallId.equals(activePeer.getCallId());
    }

    public abstract void onSuccessContinue(V result);
    public abstract void onFailureContinue(Throwable throwable);
  }

  private abstract class FailureListener<V> extends StateAwareListener<V> {
    FailureListener(CallState expectedState, CallId expectedCallId) {
      super(expectedState, expectedCallId);
    }

    @Override
    public void onSuccessContinue(V result) {}
  }

  private abstract class SuccessOnlyListener<V> extends StateAwareListener<V> {
    SuccessOnlyListener(CallState expectedState, CallId expectedCallId) {
      super(expectedState, expectedCallId);
    }

    @Override
    public void onFailureContinue(Throwable throwable) {
      Log.w(TAG, throwable);
      throw new AssertionError(throwable);
    }
  }

  private class SendCallMessageListener<V> extends StateAwareListener<V> {
    SendCallMessageListener(RemotePeer expectedRemotePeer) {
      super(expectedRemotePeer.getState(), expectedRemotePeer.getCallId());
    }

    @Override
    public void onSuccessContinue(V result) {
      if (callManager != null) {
        try {
          callManager.messageSent(getCallId());
        } catch (CallException e) {
          callFailure("callManager.messageSent() failed: ", e);
        }
      }
    }

    @Override
    public void onStateChangeContinue() {
      if (callManager != null) {
        try {
          callManager.messageSent(getCallId());
        } catch (CallException e) {
          callFailure("callManager.messageSent() failed: ", e);
        }
      }
    }

    @Override
    public void onFailureContinue(Throwable error) {
      Log.w(TAG, error);

      if (callManager != null) {
        try {
          callManager.messageSendFailure(getCallId());
        } catch (CallException e) {
          callFailure("callManager.messageSendFailure() failed: ", e);
        }
      }

      if (activePeer == null) {
        return;
      }

      if (error instanceof UntrustedIdentityException) {
        sendMessage(WebRtcViewModel.State.UNTRUSTED_IDENTITY, activePeer, ((UntrustedIdentityException)error).getIdentityKey(), localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      } else if (error instanceof UnregisteredUserException) {
        sendMessage(WebRtcViewModel.State.NO_SUCH_USER, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      } else if (error instanceof IOException) {
        sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled);
      }

    }
  }

  private void sendCallMessage(RemotePeer remotePeer, Integer remoteDevice, Boolean broadcast, SignalServiceCallMessage callMessage) {
    ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(remotePeer, callMessage);
    listenableFutureTask.addListener(new SendCallMessageListener<Boolean>(remotePeer));
  }

  private LockManager.PhoneState getInCallPhoneState() {
    AudioManager audioManager = ServiceUtil.getAudioManager(this);
    if (audioManager.isSpeakerphoneOn() || audioManager.isBluetoothScoOn() || audioManager.isWiredHeadsetOn()) {
      return LockManager.PhoneState.IN_HANDS_FREE_CALL;
    } else {
      return LockManager.PhoneState.IN_CALL;
    }
  }

  // CallManager observer callbacks

  @Override
  public void onStartCall(Remote remote, CallId callId, Boolean isOutgoing) {
    Log.i(TAG, "onStartCall: callId: " + callId + ", outgoing: " + isOutgoing);

    if (activePeer != null) {
      throw new IllegalStateException("activePeer already set for START_OUTGOING_CALL");
    }

    if (remote instanceof RemotePeer) {
      activePeer = (RemotePeer)remote;

      Intent intent = new Intent(this, WebRtcCallService.class);

      if (isOutgoing) {
          intent.setAction(ACTION_START_OUTGOING_CALL);
          activePeer.dialing(callId);
      } else {
          intent.setAction(ACTION_START_INCOMING_CALL);
          activePeer.answering(callId);
      }

      intent.putExtra(EXTRA_REMOTE_PEER, activePeer)
            .putExtra(EXTRA_CALL_ID, callId.longValue());

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onCallEvent(Remote remote, CallEvent event) {
    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      Log.i(TAG, "onCallEvent: call_id: " + remotePeer.getCallId() + ", event: " + event);
      intent.putExtra(EXTRA_REMOTE_PEER, remotePeer);

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
        case ENDED_RECEIVED_OFFER_EXPIRED:
          intent.setAction(ACTION_ENDED_RX_OFFER_EXPIRED);
          break;
        case ENDED_RECEIVED_OFFER_WHILE_ACTIVE:
          intent.setAction(ACTION_ENDED_RX_OFFER_WHILE_ACTIVE);
          break;
        case ENDED_LOCAL_HANGUP:
        case ENDED_APP_DROPPED_CALL:
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
  public void onCallConcluded(Remote remote) {
    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      Log.i(TAG, "onCallConcluded: call_id: " + remotePeer.getCallId());
      intent.setAction(ACTION_CALL_CONCLUDED)
            .putExtra(EXTRA_REMOTE_PEER, remotePeer);
      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendOffer(CallId callId, Remote remote, Integer remoteDevice, Boolean broadcast, String offer) {
    Log.i(TAG, "onSendOffer: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_OFFER)
            .putExtra(EXTRA_CALL_ID,           callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,       remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE,     remoteDevice)
            .putExtra(EXTRA_BROADCAST,         broadcast)
            .putExtra(EXTRA_OFFER_DESCRIPTION, offer);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendAnswer(CallId callId, Remote remote, Integer remoteDevice, Boolean broadcast, String answer) {
    Log.i(TAG, "onSendAnswer: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_ANSWER)
            .putExtra(EXTRA_CALL_ID,            callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,        remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE,      remoteDevice)
            .putExtra(EXTRA_BROADCAST,          broadcast)
            .putExtra(EXTRA_ANSWER_DESCRIPTION, answer);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendIceCandidates(CallId callId, Remote remote, Integer remoteDevice, Boolean broadcast, List<IceCandidate> iceCandidates) {
    Log.i(TAG, "onSendIceCandidates: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      ArrayList<IceCandidateParcel> iceCandidateParcels = new ArrayList<>(iceCandidates.size());
      for (IceCandidate iceCandidate : iceCandidates) {
        iceCandidateParcels.add(new IceCandidateParcel(iceCandidate));
      }

      intent.setAction(ACTION_SEND_ICE_CANDIDATES)
            .putExtra(EXTRA_CALL_ID,           callId.longValue())
            .putExtra(EXTRA_REMOTE_PEER,       remotePeer)
            .putExtra(EXTRA_REMOTE_DEVICE,     remoteDevice)
            .putExtra(EXTRA_BROADCAST,         broadcast)
            .putParcelableArrayListExtra(EXTRA_ICE_CANDIDATES, iceCandidateParcels);

      startService(intent);
    } else {
      throw new AssertionError("Received remote is not instanceof RemotePeer");
    }
  }

  @Override
  public void onSendHangup(CallId callId, Remote remote, Integer remoteDevice, Boolean broadcast) {
    Log.i(TAG, "onSendHangup: id: " + callId.format(remoteDevice));

    if (remote instanceof RemotePeer) {
      RemotePeer remotePeer = (RemotePeer)remote;
      Intent     intent     = new Intent(this, WebRtcCallService.class);

      intent.setAction(ACTION_SEND_HANGUP)
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
  public void onSendBusy(CallId callId, Remote remote, Integer remoteDevice, Boolean broadcast) {
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
}
