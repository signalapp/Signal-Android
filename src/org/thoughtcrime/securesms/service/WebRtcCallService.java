package org.thoughtcrime.securesms.service;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.InvalidProtocolBufferException;

import org.thoughtcrime.redphone.RedPhoneService;
import org.thoughtcrime.redphone.audio.IncomingRinger;
import org.thoughtcrime.redphone.call.LockManager;
import org.thoughtcrime.redphone.pstn.IncomingPstnCallReceiver;
import org.thoughtcrime.redphone.util.AudioUtils;
import org.thoughtcrime.redphone.util.UncaughtExceptionHandlerManager;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.webrtc.CallNotificationManager;
import org.thoughtcrime.securesms.webrtc.PeerConnectionFactoryOptions;
import org.thoughtcrime.securesms.webrtc.PeerConnectionWrapper;
import org.thoughtcrime.securesms.webrtc.PeerConnectionWrapper.PeerConnectionException;
import org.thoughtcrime.securesms.webrtc.WebRtcDataProtos;
import org.thoughtcrime.securesms.webrtc.WebRtcDataProtos.Connected;
import org.thoughtcrime.securesms.webrtc.WebRtcDataProtos.Data;
import org.thoughtcrime.securesms.webrtc.WebRtcDataProtos.Hangup;
import org.thoughtcrime.securesms.webrtc.audio.OutgoingRinger;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoTrack;
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
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static org.thoughtcrime.securesms.webrtc.CallNotificationManager.TYPE_ESTABLISHED;
import static org.thoughtcrime.securesms.webrtc.CallNotificationManager.TYPE_INCOMING_RINGING;
import static org.thoughtcrime.securesms.webrtc.CallNotificationManager.TYPE_OUTGOING_RINGING;

public class WebRtcCallService extends Service implements InjectableType, PeerConnection.Observer, DataChannel.Observer {

  private static final String TAG = WebRtcCallService.class.getSimpleName();

  private enum CallState {
    STATE_IDLE, STATE_DIALING, STATE_ANSWERING, STATE_REMOTE_RINGING, STATE_LOCAL_RINGING, STATE_CONNECTED
  }

  private static final String DATA_CHANNEL_NAME = "signaling";

  public static final String EXTRA_REMOTE_NUMBER      = "remote_number";
  public static final String EXTRA_MUTE               = "mute_value";
  public static final String EXTRA_REMOTE_DESCRIPTION = "remote_description";
  public static final String EXTRA_TIMESTAMP          = "timestamp";
  public static final String EXTRA_CALL_ID            = "call_id";
  public static final String EXTRA_ICE_SDP            = "ice_sdp";
  public static final String EXTRA_ICE_SDP_MID        = "ice_sdp_mid";
  public static final String EXTRA_ICE_SDP_LINE_INDEX = "ice_sdp_line_index";
  public static final String EXTRA_RESULT_RECEIVER    = "result_receiver";

  public static final String ACTION_INCOMING_CALL  = "CALL_INCOMING";
  public static final String ACTION_OUTGOING_CALL  = "CALL_OUTGOING";
  public static final String ACTION_ANSWER_CALL    = "ANSWER_CALL";
  public static final String ACTION_DENY_CALL      = "DENY_CALL";
  public static final String ACTION_LOCAL_HANGUP   = "LOCAL_HANGUP";
  public static final String ACTION_SET_MUTE_AUDIO = "SET_MUTE_AUDIO";
  public static final String ACTION_SET_MUTE_VIDEO = "SET_MUTE_VIDEO";
  public static final String ACTION_CHECK_TIMEOUT     = "CHECK_TIMEOUT";
  public static final String ACTION_IS_IN_CALL_QUERY = "IS_IN_CALL";

  public static final String ACTION_RESPONSE_MESSAGE  = "RESPONSE_MESSAGE";
  public static final String ACTION_ICE_MESSAGE       = "ICE_MESSAGE";
  public static final String ACTION_ICE_CANDIDATE     = "ICE_CANDIDATE";
  public static final String ACTION_CALL_CONNECTED    = "CALL_CONNECTED";
  public static final String ACTION_REMOTE_HANGUP     = "REMOTE_HANGUP";
  public static final String ACTION_REMOTE_BUSY       = "REMOTE_BUSY";
  public static final String ACTION_REMOTE_VIDEO_MUTE = "REMOTE_VIDEO_MUTE";
  public static final String ACTION_ICE_CONNECTED     = "ICE_CONNECTED";

  private CallState callState          = CallState.STATE_IDLE;
  private boolean   audioEnabled       = true;
  private boolean   localVideoEnabled  = false;
  private boolean   remoteVideoEnabled = false;
  private Handler   serviceHandler     = new Handler();

  @Inject public SignalMessageSenderFactory  messageSenderFactory;
  @Inject public SignalServiceAccountManager accountManager;

  private SignalServiceMessageSender messageSender;
  private PeerConnectionFactory      peerConnectionFactory;
  private IncomingRinger             incomingRinger;
  private OutgoingRinger             outgoingRinger;
  private LockManager                lockManager;

  private IncomingPstnCallReceiver        callReceiver;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  @Nullable private Long                   callId;
  @Nullable private Recipient              recipient;
  @Nullable private PeerConnectionWrapper  peerConnection;
  @Nullable private DataChannel            dataChannel;
  @Nullable private List<IceUpdateMessage> pendingIceUpdates;

  @Nullable public  static SurfaceViewRenderer localRenderer;
  @Nullable public  static SurfaceViewRenderer remoteRenderer;
  @Nullable private static EglBase             eglBase;

  private ExecutorService          serviceExecutor = Executors.newSingleThreadExecutor();
  private ExecutorService          networkExecutor = Executors.newSingleThreadExecutor();
  private ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

  @Override
  public void onCreate() {
    super.onCreate();

    initializeResources();
    initializeRingers();

    registerIncomingPstnCallReceiver();
    registerUncaughtExceptionHandler();
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    Log.w(TAG, "onStartCommand...");
    if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

    serviceExecutor.execute(new Runnable() {
      @Override
      public void run() {
        if      (intent.getAction().equals(ACTION_INCOMING_CALL) && isBusy()) handleBusyCall(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_BUSY))               handleBusyMessage(intent);
        else if (intent.getAction().equals(ACTION_INCOMING_CALL))             handleIncomingCall(intent);
        else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle()) handleOutgoingCall(intent);
        else if (intent.getAction().equals(ACTION_ANSWER_CALL))               handleAnswerCall(intent);
        else if (intent.getAction().equals(ACTION_DENY_CALL))                 handleDenyCall(intent);
        else if (intent.getAction().equals(ACTION_LOCAL_HANGUP))              handleLocalHangup(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_HANGUP))             handleRemoteHangup(intent);
        else if (intent.getAction().equals(ACTION_SET_MUTE_AUDIO))            handleSetMuteAudio(intent);
        else if (intent.getAction().equals(ACTION_SET_MUTE_VIDEO))            handleSetMuteVideo(intent);
        else if (intent.getAction().equals(ACTION_REMOTE_VIDEO_MUTE))         handleRemoteVideoMute(intent);
        else if (intent.getAction().equals(ACTION_RESPONSE_MESSAGE))          handleResponseMessage(intent);
        else if (intent.getAction().equals(ACTION_ICE_MESSAGE))               handleRemoteIceCandidate(intent);
        else if (intent.getAction().equals(ACTION_ICE_CANDIDATE))             handleLocalIceCandidate(intent);
        else if (intent.getAction().equals(ACTION_ICE_CONNECTED))             handleIceConnected(intent);
        else if (intent.getAction().equals(ACTION_CALL_CONNECTED))            handleCallConnected(intent);
        else if (intent.getAction().equals(ACTION_CHECK_TIMEOUT))             handleCheckTimeout(intent);
        else if (intent.getAction().equals(ACTION_IS_IN_CALL_QUERY))          handleIsInCallQuery(intent);
      }
    });

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    if (callReceiver != null) {
      unregisterReceiver(callReceiver);
    }

    if (uncaughtExceptionHandlerManager != null) {
      uncaughtExceptionHandlerManager.unregister();
    }
  }

  // Initializers

  private void initializeRingers() {
    this.outgoingRinger = new OutgoingRinger(this);
    this.incomingRinger = new IncomingRinger(this);
  }

  private void initializeResources() {
    ApplicationContext.getInstance(this).injectDependencies(this);

    this.callState             = CallState.STATE_IDLE;
    this.lockManager           = new LockManager(this);
    this.peerConnectionFactory = new PeerConnectionFactory(new PeerConnectionFactoryOptions());
    this.messageSender         = messageSenderFactory.create();
    this.messageSender.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));
    this.accountManager.setSoTimeoutMillis(TimeUnit.SECONDS.toMillis(10));
  }

  private void registerIncomingPstnCallReceiver() {
    callReceiver = new IncomingPstnCallReceiver();
    registerReceiver(callReceiver, new IntentFilter("android.intent.action.PHONE_STATE"));
  }

  private void registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
    uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(lockManager));
  }

  // Handlers

  private void handleIncomingCall(final Intent intent) {
    Log.w(TAG, "handleIncomingCall()");
    if (callState != CallState.STATE_IDLE) throw new IllegalStateException("Incoming on non-idle");

    final String offer = intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION);

    this.callState = CallState.STATE_ANSWERING;
    this.callId    = intent.getLongExtra(EXTRA_CALL_ID, -1);
    this.recipient = getRemoteRecipient(intent);

    if (isIncomingMessageExpired(intent)) {
      insertMissedCall(this.recipient, true);
      terminate();
      return;
    }

    timeoutExecutor.schedule(new TimeoutRunnable(this.callId), 2, TimeUnit.MINUTES);

    initializeVideo();
    retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.callState, this.callId) {
      @Override
      public void onSuccessContinue(List<PeerConnection.IceServer> result) {
        try {
          boolean isSystemContact = ContactAccessor.getInstance().isSystemContact(WebRtcCallService.this, recipient.getNumber());
          boolean isAlwaysTurn    = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);

          WebRtcCallService.this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, !isSystemContact || isAlwaysTurn);
          WebRtcCallService.this.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, offer));
          WebRtcCallService.this.lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);

          SessionDescription sdp = WebRtcCallService.this.peerConnection.createAnswer(new MediaConstraints());
          Log.w(TAG, "Answer SDP: " + sdp.description);
          WebRtcCallService.this.peerConnection.setLocalDescription(sdp);

          ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forAnswer(new AnswerMessage(WebRtcCallService.this.callId, sdp.description)));

          listenableFutureTask.addListener(new FailureListener<Boolean>(WebRtcCallService.this.callState, WebRtcCallService.this.callId) {
            @Override
            public void onFailureContinue(Throwable error) {
              Log.w(TAG, error);
              terminate();
            }
          });
        } catch (PeerConnectionException e) {
          Log.w(TAG, e);
          terminate();
        }
      }
    });
  }

  private void handleOutgoingCall(Intent intent) {
    Log.w(TAG, "handleOutgoingCall...");

    if (callState != CallState.STATE_IDLE) throw new IllegalStateException("Dialing from non-idle?");

    try {
      this.callState         = CallState.STATE_DIALING;
      this.recipient         = getRemoteRecipient(intent);
      this.callId            = SecureRandom.getInstance("SHA1PRNG").nextLong();
      this.pendingIceUpdates = new LinkedList<>();

      initializeVideo();

      sendMessage(WebRtcViewModel.State.CALL_OUTGOING, recipient, localVideoEnabled, remoteVideoEnabled);
      lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
      outgoingRinger.playSonar();

      CallNotificationManager.setCallInProgress(this, TYPE_OUTGOING_RINGING, recipient);
      DatabaseFactory.getSmsDatabase(this).insertOutgoingCall(recipient.getNumber());

      timeoutExecutor.schedule(new TimeoutRunnable(this.callId), 2, TimeUnit.MINUTES);

      retrieveTurnServers().addListener(new SuccessOnlyListener<List<PeerConnection.IceServer>>(this.callState, this.callId) {
        @Override
        public void onSuccessContinue(List<PeerConnection.IceServer> result) {
          try {
            boolean isAlwaysTurn = TextSecurePreferences.isTurnOnly(WebRtcCallService.this);

            WebRtcCallService.this.peerConnection = new PeerConnectionWrapper(WebRtcCallService.this, peerConnectionFactory, WebRtcCallService.this, localRenderer, result, isAlwaysTurn);
            WebRtcCallService.this.dataChannel    = WebRtcCallService.this.peerConnection.createDataChannel(DATA_CHANNEL_NAME);
            WebRtcCallService.this.dataChannel.registerObserver(WebRtcCallService.this);

            SessionDescription sdp = WebRtcCallService.this.peerConnection.createOffer(new MediaConstraints());
            WebRtcCallService.this.peerConnection.setLocalDescription(sdp);

            Log.w(TAG, "Sending offer: " + sdp.description);

            ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forOffer(new OfferMessage(WebRtcCallService.this.callId, sdp.description)));

            listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
              @Override
              public void onFailureContinue(Throwable error) {
                Log.w(TAG, error);

                if (error instanceof UntrustedIdentityException) {
                  sendMessage(WebRtcViewModel.State.UNTRUSTED_IDENTITY, recipient, ((UntrustedIdentityException)error).getIdentityKey(), localVideoEnabled, remoteVideoEnabled);
                } else if (error instanceof UnregisteredUserException) {
                  sendMessage(WebRtcViewModel.State.NO_SUCH_USER, recipient, localVideoEnabled, remoteVideoEnabled);
                } else if (error instanceof IOException) {
                  sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localVideoEnabled, remoteVideoEnabled);
                }

                terminate();
              }
            });
          } catch (PeerConnectionException e) {
            Log.w(TAG, e);
            terminate();
          }
        }
      });
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private void handleResponseMessage(Intent intent) {
    try {
      Log.w(TAG, "Got response: " + intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION));

      if (callState != CallState.STATE_DIALING || !getRemoteRecipient(intent).equals(recipient) || !Util.isEquals(this.callId, getCallId(intent))) {
        Log.w(TAG, "Got answer for recipient and call id we're not currently dialing: " + getCallId(intent) + ", " + getRemoteRecipient(intent));
        return;
      }

      if (peerConnection == null || pendingIceUpdates == null) {
        throw new AssertionError("assert");
      }

      if (!pendingIceUpdates.isEmpty()) {
        ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forIceUpdates(pendingIceUpdates));

        listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
          @Override
          public void onFailureContinue(Throwable error) {
            Log.w(TAG, error);
            sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localVideoEnabled, remoteVideoEnabled);

            terminate();
          }
        });
      }

      this.peerConnection.setRemoteDescription(new SessionDescription(SessionDescription.Type.ANSWER, intent.getStringExtra(EXTRA_REMOTE_DESCRIPTION)));
      this.pendingIceUpdates = null;
    } catch (PeerConnectionException e) {
      Log.w(TAG, e);
      terminate();
    }
  }

  private void handleRemoteIceCandidate(Intent intent) {
    Log.w(TAG, "handleRemoteIceCandidate...");

    if (peerConnection != null && Util.isEquals(this.callId, getCallId(intent))) {
      peerConnection.addIceCandidate(new IceCandidate(intent.getStringExtra(EXTRA_ICE_SDP_MID),
                                                      intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
                                                      intent.getStringExtra(EXTRA_ICE_SDP)));
    }
  }

  private void handleLocalIceCandidate(Intent intent) {
    if (callState == CallState.STATE_IDLE || !Util.isEquals(this.callId, getCallId(intent))) {
      Log.w(TAG, "State is now idle, ignoring ice candidate...");
    }

    if (recipient == null || callId == null) {
      throw new AssertionError("assert");
    }

    IceUpdateMessage iceUpdateMessage = new IceUpdateMessage(this.callId, intent.getStringExtra(EXTRA_ICE_SDP_MID),
                                                             intent.getIntExtra(EXTRA_ICE_SDP_LINE_INDEX, 0),
                                                             intent.getStringExtra(EXTRA_ICE_SDP));

    if (pendingIceUpdates != null) {
      this.pendingIceUpdates.add(iceUpdateMessage);
      return;
    }

    ListenableFutureTask<Boolean> listenableFutureTask = sendMessage(recipient, SignalServiceCallMessage.forIceUpdate(iceUpdateMessage));

    listenableFutureTask.addListener(new FailureListener<Boolean>(callState, callId) {
      @Override
      public void onFailureContinue(Throwable error) {
        Log.w(TAG, error);
        sendMessage(WebRtcViewModel.State.NETWORK_FAILURE, recipient, localVideoEnabled, remoteVideoEnabled);

        terminate();
      }
    });
  }

  private void handleIceConnected(Intent intent) {
    if (callState == CallState.STATE_ANSWERING) {
      if (this.recipient == null) throw new AssertionError("assert");

      this.callState = CallState.STATE_LOCAL_RINGING;
      this.lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

      sendMessage(WebRtcViewModel.State.CALL_INCOMING, recipient, localVideoEnabled, remoteVideoEnabled);
      startCallCardActivity();
      incomingRinger.start();
      CallNotificationManager.setCallInProgress(this, TYPE_INCOMING_RINGING, recipient);
    } else if (callState == CallState.STATE_DIALING) {
      if (this.recipient == null) throw new AssertionError("assert");

      this.callState = CallState.STATE_REMOTE_RINGING;
      this.outgoingRinger.playRing();

      sendMessage(WebRtcViewModel.State.CALL_RINGING, recipient, localVideoEnabled, remoteVideoEnabled);
    }
  }

  private void handleCallConnected(Intent intent) {
    if (callState != CallState.STATE_REMOTE_RINGING && callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Ignoring call connected for unknown state: " + callState);
      return;
    }

    if (!Util.isEquals(this.callId, getCallId(intent))) {
      Log.w(TAG, "Ignoring connected for unknown call id: " + getCallId(intent));
      return;
    }

    if (recipient == null || peerConnection == null || dataChannel == null) {
      throw new AssertionError("assert");
    }

    callState = CallState.STATE_CONNECTED;

    initializeAudio();
    outgoingRinger.playComplete();
    lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);

    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, recipient, localVideoEnabled, remoteVideoEnabled);

    CallNotificationManager.setCallInProgress(this, TYPE_ESTABLISHED, recipient);

    this.peerConnection.setAudioEnabled(audioEnabled);
    this.peerConnection.setVideoEnabled(localVideoEnabled);

    this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder()
                                                                     .setVideoStreamingStatus(WebRtcDataProtos.VideoStreamingStatus.newBuilder()
                                                                                                                                   .setId(this.callId)
                                                                                                                                   .setEnabled(localVideoEnabled))
                                                                     .build().toByteArray()), false));
  }

  private void handleBusyCall(Intent intent) {
    Recipient recipient = getRemoteRecipient(intent);
    long      callId    = getCallId(intent);

    sendMessage(recipient, SignalServiceCallMessage.forBusy(new BusyMessage(callId)));
    insertMissedCall(getRemoteRecipient(intent), false);
  }

  private void handleBusyMessage(Intent intent) {
    Log.w(TAG, "handleBusyMessage...");

    Recipient recipient = getRemoteRecipient(intent);
    long      callId    = getCallId(intent);

    if (callState != CallState.STATE_DIALING || !Util.isEquals(this.callId, callId) || !recipient.equals(this.recipient)) {
      Log.w(TAG, "Got busy message for inactive session...");
      return;
    }

    sendMessage(WebRtcViewModel.State.CALL_BUSY, recipient, localVideoEnabled, remoteVideoEnabled);

    outgoingRinger.playBusy();
    serviceHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        WebRtcCallService.this.terminate();
      }
    }, WebRtcCallActivity.BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleCheckTimeout(Intent intent) {
    if (this.callId != null                                   &&
        this.callId == intent.getLongExtra(EXTRA_CALL_ID, -1) &&
        this.callState != CallState.STATE_CONNECTED)
    {
      Log.w(TAG, "Timing out call: " + this.callId);
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled);
      terminate();
    }
  }

  private void handleIsInCallQuery(Intent intent) {
    ResultReceiver resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);

    if (resultReceiver != null) {
      resultReceiver.send(callState != CallState.STATE_IDLE ? 1 : 0, null);
    }
  }

  private void insertMissedCall(@NonNull Recipient recipient, boolean signal) {
    Pair<Long, Long> messageAndThreadId = DatabaseFactory.getSmsDatabase(this).insertMissedCall(recipient.getNumber());
    MessageNotifier.updateNotification(this, KeyCachingService.getMasterSecret(this),
                                       false, messageAndThreadId.second, signal);
  }

  private void handleAnswerCall(Intent intent) {
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only answer from ringing!");
      return;
    }

    if (peerConnection == null || dataChannel == null || recipient == null || callId == null) {
      throw new AssertionError("assert");
    }

    incomingRinger.stop();

    DatabaseFactory.getSmsDatabase(this).insertReceivedCall(recipient.getNumber());

    this.peerConnection.setAudioEnabled(true);
    this.peerConnection.setVideoEnabled(true);
    this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder().setConnected(Connected.newBuilder().setId(this.callId)).build().toByteArray()), false));

    intent.putExtra(EXTRA_CALL_ID, callId);
    intent.putExtra(EXTRA_REMOTE_NUMBER, recipient.getNumber());
    handleCallConnected(intent);
  }

  private void handleDenyCall(Intent intent) {
    if (callState != CallState.STATE_LOCAL_RINGING) {
      Log.w(TAG, "Can only deny from ringing!");
      return;
    }

    if (recipient == null || callId == null || dataChannel == null) {
      throw new AssertionError("assert");
    }

    this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder().setHangup(Hangup.newBuilder().setId(this.callId)).build().toByteArray()), false));
    sendMessage(this.recipient, SignalServiceCallMessage.forHangup(new HangupMessage(this.callId)));

    DatabaseFactory.getSmsDatabase(this).insertMissedCall(recipient.getNumber());

    this.terminate();
  }

  private void handleLocalHangup(Intent intent) {
    if (this.dataChannel != null && this.recipient != null && this.callId != null) {
      this.accountManager.cancelInFlightRequests();
      this.messageSender.cancelInFlightRequests();

      this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder().setHangup(Hangup.newBuilder().setId(this.callId)).build().toByteArray()), false));
      sendMessage(this.recipient, SignalServiceCallMessage.forHangup(new HangupMessage(this.callId)));
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled);
    }

    terminate();
  }

  private void handleRemoteHangup(Intent intent) {
    if (!Util.isEquals(this.callId, getCallId(intent))) {
      Log.w(TAG, "hangup for non-active call...");
      return;
    }

    if (this.recipient == null) {
      throw new AssertionError("assert");
    }

    if (this.callState == CallState.STATE_DIALING || this.callState == CallState.STATE_REMOTE_RINGING) {
      sendMessage(WebRtcViewModel.State.RECIPIENT_UNAVAILABLE, this.recipient, localVideoEnabled, remoteVideoEnabled);
    } else {
      sendMessage(WebRtcViewModel.State.CALL_DISCONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled);
    }

    if (this.callState == CallState.STATE_ANSWERING || this.callState == CallState.STATE_LOCAL_RINGING) {
      insertMissedCall(this.recipient, true);
    }

    this.terminate();
  }

  private void handleSetMuteAudio(Intent intent) {
    boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);
    this.audioEnabled = !muted;

    if (this.peerConnection != null) {
      this.peerConnection.setAudioEnabled(this.audioEnabled);
    }
  }

  private void handleSetMuteVideo(Intent intent) {
    boolean muted = intent.getBooleanExtra(EXTRA_MUTE, false);
    this.localVideoEnabled = !muted;

    if (this.peerConnection != null) {
      this.peerConnection.setVideoEnabled(this.localVideoEnabled);
    }

    if (this.callId != null && this.dataChannel != null) {
      this.dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(Data.newBuilder()
                                                                       .setVideoStreamingStatus(WebRtcDataProtos.VideoStreamingStatus.newBuilder()
                                                                                                                                     .setId(this.callId)
                                                                                                                                     .setEnabled(localVideoEnabled))
                                                                       .build().toByteArray()), false));
    }

    sendMessage(viewModelStateFor(callState), this.recipient, localVideoEnabled, remoteVideoEnabled);
  }

  private void handleRemoteVideoMute(Intent intent) {
    boolean muted  = intent.getBooleanExtra(EXTRA_MUTE, false);
    long    callId = intent.getLongExtra(EXTRA_CALL_ID, -1);

    if (this.recipient == null || this.callState != CallState.STATE_CONNECTED || !Util.isEquals(this.callId, callId)) {
      Log.w(TAG, "Got video toggle for inactive call, ignoring...");
      return;
    }

    this.remoteVideoEnabled = !muted;
    sendMessage(WebRtcViewModel.State.CALL_CONNECTED, this.recipient, localVideoEnabled, remoteVideoEnabled);
  }

  /// Helper Methods

  private boolean isBusy() {
    TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);

    return callState != CallState.STATE_IDLE                                   ||
           telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE ||
           RedPhoneService.isCallActive(this);
  }

  private boolean isIdle() {
    return callState == CallState.STATE_IDLE;
  }

  private boolean isIncomingMessageExpired(Intent intent) {
    return System.currentTimeMillis() - intent.getLongExtra(WebRtcCallService.EXTRA_TIMESTAMP, -1) > TimeUnit.MINUTES.toMillis(2);
  }

  private void initializeAudio() {
    AudioManager audioManager = ServiceUtil.getAudioManager(this);
    AudioUtils.resetConfiguration(this);

    Log.d(TAG, "request STREAM_VOICE_CALL transient audio focus");
    audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                                   AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
  }

  private void initializeVideo() {
    Util.runOnMainSync(new Runnable() {
      @Override
      public void run() {
        eglBase        = EglBase.create();
        localRenderer  = new SurfaceViewRenderer(WebRtcCallService.this);
        remoteRenderer = new SurfaceViewRenderer(WebRtcCallService.this);

        localRenderer.init(eglBase.getEglBaseContext(), null);
        remoteRenderer.init(eglBase.getEglBaseContext(), null);

        peerConnectionFactory.setVideoHwAccelerationOptions(eglBase.getEglBaseContext(),
                                                            eglBase.getEglBaseContext());
      }
    });
  }

  private void shutdownAudio() {
    Log.d(TAG, "reset audio mode and abandon focus");
    AudioUtils.resetConfiguration(this);
    AudioManager am = ServiceUtil.getAudioManager(this);
    am.setMode(AudioManager.MODE_NORMAL);
    am.abandonAudioFocus(null);
    am.stopBluetoothSco();
  }

  private synchronized void terminate() {
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    CallNotificationManager.setCallEnded(this);

    incomingRinger.stop();
    outgoingRinger.stop();
    outgoingRinger.playDisconnected();

    if (peerConnection != null) {
      peerConnection.dispose();
      peerConnection = null;
    }

    if (eglBase != null && localRenderer != null && remoteRenderer != null) {
      localRenderer.release();
      remoteRenderer.release();
      eglBase.release();

      localRenderer  = null;
      remoteRenderer = null;
      eglBase        = null;
    }

    shutdownAudio();

    this.callState          = CallState.STATE_IDLE;
    this.recipient          = null;
    this.callId             = null;
    this.audioEnabled       = true;
    this.localVideoEnabled  = false;
    this.remoteVideoEnabled = false;
    this.pendingIceUpdates  = null;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
  }


  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull Recipient recipient,
                           boolean localVideoEnabled, boolean remoteVideoEnabled)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state, recipient, localVideoEnabled, remoteVideoEnabled));
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull Recipient recipient,
                           @NonNull IdentityKey identityKey,
                           boolean localVideoEnabled, boolean remoteVideoEnabled)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state, recipient, identityKey, localVideoEnabled, remoteVideoEnabled));
  }

  private ListenableFutureTask<Boolean> sendMessage(@NonNull final Recipient recipient,
                                                    @NonNull final SignalServiceCallMessage callMessage)
  {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        try {
          String number = Util.canonicalizeNumber(WebRtcCallService.this, recipient.getNumber());
          messageSender.sendCallMessage(new SignalServiceAddress(number), callMessage);
          return true;
        } catch (InvalidNumberException e) {
          throw new IOException(e);
        }
      }
    };

    ListenableFutureTask<Boolean> listenableFutureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(listenableFutureTask);

    return listenableFutureTask;
  }

  private void startCallCardActivity() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, WebRtcCallActivity.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    this.startActivity(activityIntent);
  }

  ///

  private @NonNull Recipient getRemoteRecipient(Intent intent) {
    String remoteNumber = intent.getStringExtra(EXTRA_REMOTE_NUMBER);
    if (TextUtils.isEmpty(remoteNumber)) throw new AssertionError("No recipient in intent!");

    Recipients recipients = RecipientFactory.getRecipientsFromString(this, remoteNumber, true);
    Recipient  result     = recipients.getPrimaryRecipient();

    if (result == null) throw new AssertionError("Recipient lookup failed!");
    else                return result;
  }

  private long getCallId(Intent intent) {
    return intent.getLongExtra(EXTRA_CALL_ID, -1);
  }


  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /// PeerConnection Observer
  @Override
  public void onSignalingChange(PeerConnection.SignalingState newState) {
    Log.w(TAG, "onSignalingChange: " + newState);
  }

  @Override
  public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
    Log.w(TAG, "onIceConnectionChange:" + newState);

    if (newState == PeerConnection.IceConnectionState.CONNECTED ||
        newState == PeerConnection.IceConnectionState.COMPLETED)
    {
      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.setAction(ACTION_ICE_CONNECTED);

      startService(intent);
    } else if (newState == PeerConnection.IceConnectionState.FAILED) {
      Intent intent = new Intent(this, WebRtcCallService.class);
      intent.setAction(ACTION_REMOTE_HANGUP);
      intent.putExtra(EXTRA_CALL_ID, this.callId);

      startService(intent);
    }
  }

  @Override
  public void onIceConnectionReceivingChange(boolean receiving) {
    Log.w(TAG, "onIceConnectionReceivingChange:" + receiving);
  }

  @Override
  public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
    Log.w(TAG, "onIceGatheringChange:" + newState);

  }

  @Override
  public void onIceCandidate(IceCandidate candidate) {
    Log.w(TAG, "onIceCandidate:" + candidate);
    Intent intent = new Intent(this, WebRtcCallService.class);

    intent.setAction(ACTION_ICE_CANDIDATE);
    intent.putExtra(EXTRA_ICE_SDP_MID, candidate.sdpMid);
    intent.putExtra(EXTRA_ICE_SDP_LINE_INDEX, candidate.sdpMLineIndex);
    intent.putExtra(EXTRA_ICE_SDP, candidate.sdp);

    startService(intent);
  }

  @Override
  public void onIceCandidatesRemoved(IceCandidate[] candidates) {
    Log.w(TAG, "onIceCandidatesRemoved:" + (candidates != null ? candidates.length : null));
  }

  @Override
  public void onAddStream(MediaStream stream) {
    Log.w(TAG, "onAddStream:" + stream);

    for (AudioTrack audioTrack : stream.audioTracks) {
      audioTrack.setEnabled(true);
    }

    if (stream.videoTracks != null && stream.videoTracks.size() == 1) {
      VideoTrack videoTrack = stream.videoTracks.getFirst();
      videoTrack.setEnabled(true);
      videoTrack.addRenderer(new VideoRenderer(remoteRenderer));
    }

  }

  @Override
  public void onRemoveStream(MediaStream stream) {
    Log.w(TAG, "onRemoveStream:" + stream);
  }

  @Override
  public void onDataChannel(DataChannel dataChannel) {
    Log.w(TAG, "onDataChannel:" + dataChannel.label());

    if (dataChannel.label().equals(DATA_CHANNEL_NAME)) {
      this.dataChannel = dataChannel;
      this.dataChannel.registerObserver(this);
    }
  }

  @Override
  public void onRenegotiationNeeded() {
    Log.w(TAG, "onRenegotiationNeeded");
    // TODO renegotiate
  }

  @Override
  public void onBufferedAmountChange(long l) {
    Log.w(TAG, "onBufferedAmountChange: " + l);
  }

  @Override
  public void onStateChange() {
    Log.w(TAG, "onStateChange");
  }

  @Override
  public void onMessage(DataChannel.Buffer buffer) {
    Log.w(TAG, "onMessage...");

    try {
      byte[] data = new byte[buffer.data.remaining()];
      buffer.data.get(data);

      Data dataMessage = Data.parseFrom(data);

      if (dataMessage.hasConnected()) {
        Log.w(TAG, "hasConnected...");
        Intent intent = new Intent(this, WebRtcCallService.class);
        intent.setAction(ACTION_CALL_CONNECTED);
        intent.putExtra(EXTRA_CALL_ID, dataMessage.getConnected().getId());
        startService(intent);
      } else if (dataMessage.hasHangup()) {
        Log.w(TAG, "hasHangup...");
        Intent intent = new Intent(this, WebRtcCallService.class);
        intent.setAction(ACTION_REMOTE_HANGUP);
        intent.putExtra(EXTRA_CALL_ID, dataMessage.getHangup().getId());
        startService(intent);
      } else if (dataMessage.hasVideoStreamingStatus()) {
        Log.w(TAG, "hasVideoStreamingStatus...");
        Intent intent = new Intent(this, WebRtcCallService.class);
        intent.setAction(ACTION_REMOTE_VIDEO_MUTE);
        intent.putExtra(EXTRA_CALL_ID, dataMessage.getVideoStreamingStatus().getId());
        intent.putExtra(EXTRA_MUTE, !dataMessage.getVideoStreamingStatus().getEnabled());
        startService(intent);
      }
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, e);
    }
  }

  private ListenableFutureTask<List<PeerConnection.IceServer>> retrieveTurnServers() {
    Callable<List<PeerConnection.IceServer>> callable = new Callable<List<PeerConnection.IceServer>>() {
      @Override
      public List<PeerConnection.IceServer> call() {
        LinkedList<PeerConnection.IceServer> results = new LinkedList<>();

        try {
          TurnServerInfo turnServerInfo = accountManager.getTurnServerInfo();

          for (String url : turnServerInfo.getUrls()) {
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
      }
    };

    ListenableFutureTask<List<PeerConnection.IceServer>> futureTask = new ListenableFutureTask<>(callable, null, serviceExecutor);
    networkExecutor.execute(futureTask);

    return futureTask;
  }

  private WebRtcViewModel.State viewModelStateFor(CallState state) {
    switch (state) {
      case STATE_CONNECTED:      return WebRtcViewModel.State.CALL_CONNECTED;
      case STATE_DIALING:        return WebRtcViewModel.State.CALL_OUTGOING;
      case STATE_REMOTE_RINGING: return WebRtcViewModel.State.CALL_RINGING;
      case STATE_LOCAL_RINGING:  return WebRtcViewModel.State.CALL_INCOMING;
      case STATE_ANSWERING:      return WebRtcViewModel.State.CALL_INCOMING;
      case STATE_IDLE:           return WebRtcViewModel.State.CALL_DISCONNECTED;
    }

    return WebRtcViewModel.State.CALL_DISCONNECTED;
  }

  private class TimeoutRunnable implements Runnable {

    private final long callId;

    private TimeoutRunnable(long callId) {
      this.callId = callId;
    }

    public void run() {
      Intent intent = new Intent(WebRtcCallService.this, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_CHECK_TIMEOUT);
      intent.putExtra(EXTRA_CALL_ID, callId);
      startService(intent);
    }
  }

  private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
    private final LockManager lockManager;

    private ProximityLockRelease(LockManager lockManager) {
      this.lockManager = lockManager;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
      Log.d(TAG, "Uncaught exception - releasing proximity lock", throwable);
      lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
    }
  }

  private abstract class StateAwareListener<V> implements FutureTaskListener<V> {

    private final CallState expectedState;
    private final long      expectedCallId;

    StateAwareListener(CallState expectedState, long expectedCallId) {
      this.expectedState  = expectedState;
      this.expectedCallId = expectedCallId;
    }


    @Override
    public void onSuccess(V result) {
      if (!isConsistentState()) {
        Log.w(TAG, "State has changed since request, aborting success callback...");
      } else {
        onSuccessContinue(result);
      }
    }

    @Override
    public void onFailure(ExecutionException throwable) {
      if (!isConsistentState()) {
        Log.w(TAG, throwable);
        Log.w(TAG, "State has changed since request, aborting failure callback...");
      } else {
        onFailureContinue(throwable.getCause());
      }
    }

    private boolean isConsistentState() {
      return this.expectedState == callState && Util.isEquals(callId, this.expectedCallId);
    }

    public abstract void onSuccessContinue(V result);
    public abstract void onFailureContinue(Throwable throwable);
  }

  private abstract class FailureListener<V> extends StateAwareListener<V> {
    FailureListener(CallState expectedState, long expectedCallId) {
      super(expectedState, expectedCallId);
    }

    @Override
    public void onSuccessContinue(V result) {}
  }

  private abstract class SuccessOnlyListener<V> extends StateAwareListener<V> {
    SuccessOnlyListener(CallState expectedState, long expectedCallId) {
      super(expectedState, expectedCallId);
    }

    @Override
    public void onFailureContinue(Throwable throwable) {
      Log.w(TAG, throwable);
      throw new AssertionError(throwable);
    }
  }

  @WorkerThread
  public static boolean isCallActive(Context context) {
    Log.w(TAG, "isCallActive()");

    HandlerThread handlerThread = null;

    try {
      handlerThread = new HandlerThread("webrtc-callback");
      handlerThread.start();

      final SettableFuture<Boolean> future = new SettableFuture<>();

      ResultReceiver resultReceiver = new ResultReceiver(new Handler(handlerThread.getLooper())) {
        protected void onReceiveResult(int resultCode, Bundle resultData) {
          Log.w(TAG, "Got result...");
          future.set(resultCode == 1);
        }
      };

      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(ACTION_IS_IN_CALL_QUERY);
      intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);

      context.startService(intent);

      Log.w(TAG, "Blocking on result...");
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      Log.w(TAG, e);
      return false;
    } finally {
      if (handlerThread != null) handlerThread.quit();
    }
  }

  public static void isCallActive(Context context, ResultReceiver resultReceiver) {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(ACTION_IS_IN_CALL_QUERY);
    intent.putExtra(EXTRA_RESULT_RECEIVER, resultReceiver);

    context.startService(intent);
  }
}
