package org.thoughtcrime.securesms.service.webrtc;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ResultReceiver;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.HttpHeader;
import org.signal.ringrtc.NetworkRoute;
import org.signal.ringrtc.Remote;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.crypto.UnidentifiedAccessUtil;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.jobs.GroupCallUpdateSendJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.messages.GroupSendUtil;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.rx.RxStore;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.PeerConnection;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.jvm.functions.Function1;

import static org.thoughtcrime.securesms.events.WebRtcViewModel.GroupCallState.IDLE;
import static org.thoughtcrime.securesms.events.WebRtcViewModel.State.CALL_INCOMING;
import static org.thoughtcrime.securesms.events.WebRtcViewModel.State.NETWORK_FAILURE;
import static org.thoughtcrime.securesms.events.WebRtcViewModel.State.NO_SUCH_USER;
import static org.thoughtcrime.securesms.events.WebRtcViewModel.State.UNTRUSTED_IDENTITY;
import static org.thoughtcrime.securesms.service.webrtc.WebRtcUtil.getUrgencyFromCallUrgency;

/**
 * Entry point for all things calling. Lives for the life of the app instance and will spin up a foreground service when needed to
 * handle "active" calls.
 */
public final class SignalCallManager implements CallManager.Observer, GroupCall.Observer, CameraEventListener, AppForegroundObserver.Listener {

  private static final String TAG = Log.tag(SignalCallManager.class);

  public static final int BUSY_TONE_LENGTH = 2000;

  @Nullable private final CallManager callManager;

  private final Context                     context;
  private final ExecutorService             serviceExecutor;
  private final Executor                    networkExecutor;
  private final LockManager                 lockManager;

  private WebRtcServiceState            serviceState;
  private RxStore<WebRtcEphemeralState> ephemeralStateStore;
  private boolean                       needsToSetSelfUuid  = true;

  public SignalCallManager(@NonNull Application application) {
    this.context             = application.getApplicationContext();
    this.lockManager         = new LockManager(this.context);
    this.serviceExecutor     = Executors.newSingleThreadExecutor();
    this.networkExecutor     = Executors.newSingleThreadExecutor();
    this.ephemeralStateStore = new RxStore<>(new WebRtcEphemeralState(), Schedulers.from(serviceExecutor));

    CallManager callManager = null;
    try {
      callManager = CallManager.createCallManager(this);
    } catch (CallException e) {
      Log.w(TAG, "Unable to create CallManager", e);
    }
    this.callManager = callManager;

    this.serviceState = new WebRtcServiceState(new IdleActionProcessor(new WebRtcInteractor(this.context,
                                                                                            this,
                                                                                            lockManager,
                                                                                            this,
                                                                                            this,
                                                                                            this)));
  }

  public @NonNull Flowable<WebRtcEphemeralState> ephemeralStates() {
    return ephemeralStateStore.getStateFlowable().distinctUntilChanged();
  }

  @NonNull CallManager getRingRtcCallManager() {
    //noinspection ConstantConditions
    return callManager;
  }

  @NonNull LockManager getLockManager() {
    return lockManager;
  }

  private void process(@NonNull ProcessAction action) {
    Throwable t = new Throwable();
    String caller = t.getStackTrace().length > 1 ? t.getStackTrace()[1].getMethodName() : "unknown";

    if (callManager == null) {
      Log.w(TAG, "Unable to process action, call manager is not initialized");
      return;
    }

    serviceExecutor.execute(() -> {
      if (needsToSetSelfUuid) {
        try {
          callManager.setSelfUuid(SignalStore.account().requireAci().uuid());
          needsToSetSelfUuid = false;
        } catch (CallException e) {
          Log.w(TAG, "Unable to set self UUID on CallManager", e);
        }
      }

      Log.v(TAG, "Processing action: " + caller + ", handler: " + serviceState.getActionProcessor().getTag());
      WebRtcServiceState previous = serviceState;
      serviceState = action.process(previous, previous.getActionProcessor());

      if (previous != serviceState) {
        if (serviceState.getCallInfoState().getCallState() != WebRtcViewModel.State.IDLE) {
          postStateUpdate(serviceState);
        }
      }
    });
  }

  /**
   * Processes the given update to {@link WebRtcEphemeralState}.
   *
   * @param transformer The transformation to apply to the state. Runs on the {@link #serviceExecutor}.
   */
  @AnyThread
private void processStateless(@NonNull Function1<WebRtcEphemeralState, WebRtcEphemeralState> transformer) {
    ephemeralStateStore.update(transformer);
  }

  public void startPreJoinCall(@NonNull Recipient recipient) {
    process((s, p) -> p.handlePreJoinCall(s, new RemotePeer(recipient.getId())));
  }

  public void startOutgoingAudioCall(@NonNull Recipient recipient) {
    process((s, p) -> p.handleOutgoingCall(s, new RemotePeer(recipient.getId()), OfferMessage.Type.AUDIO_CALL));
  }

  public void startOutgoingVideoCall(@NonNull Recipient recipient) {
    process((s, p) -> p.handleOutgoingCall(s, new RemotePeer(recipient.getId()), OfferMessage.Type.VIDEO_CALL));
  }

  public void cancelPreJoin() {
    process((s, p) -> p.handleCancelPreJoinCall(s));
  }

  public void updateRenderedResolutions() {
    process((s, p) -> p.handleUpdateRenderedResolutions(s));
  }

  public void orientationChanged(boolean isLandscapeEnabled, int degrees) {
    process((s, p) -> p.handleOrientationChanged(s, isLandscapeEnabled, degrees));
  }

  public void setMuteAudio(boolean enabled) {
    process((s, p) -> p.handleSetMuteAudio(s, enabled));
  }

  public void setMuteVideo(boolean enabled) {
    process((s, p) -> p.handleSetEnableVideo(s, enabled));
  }

  public void flipCamera() {
    process((s, p) -> p.handleSetCameraFlip(s));
  }

  public void acceptCall(boolean answerWithVideo) {
    process((s, p) -> p.handleAcceptCall(s, answerWithVideo));
  }

  public void denyCall() {
    process((s, p) -> p.handleDenyCall(s));
  }

  public void localHangup() {
    process((s, p) -> p.handleLocalHangup(s));
  }

  public void requestUpdateGroupMembers() {
    process((s, p) -> p.handleGroupRequestUpdateMembers(s));
  }

  public void groupApproveSafetyChange(@NonNull List<RecipientId> changedRecipients) {
    process((s, p) -> p.handleGroupApproveSafetyNumberChange(s, changedRecipients));
  }

  public void isCallActive(@Nullable ResultReceiver resultReceiver) {
    process((s, p) -> p.handleIsInCallQuery(s, resultReceiver));
  }

  public void networkChange(boolean available) {
    process((s, p) -> p.handleNetworkChanged(s, available));
  }

  public void bandwidthModeUpdate() {
    process((s, p) -> p.handleBandwidthModeUpdate(s));
  }

  public void screenOff() {
    process((s, p) -> p.handleScreenOffChange(s));
  }

  public void postStateUpdate(@NonNull WebRtcServiceState state) {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state));
  }

  public void receivedOffer(@NonNull WebRtcData.CallMetadata callMetadata,
                            @NonNull WebRtcData.OfferMetadata offerMetadata,
                            @NonNull WebRtcData.ReceivedOfferMetadata receivedOfferMetadata)
  {
    process((s, p) -> p.handleReceivedOffer(s, callMetadata, offerMetadata, receivedOfferMetadata));
  }

  public void receivedAnswer(@NonNull WebRtcData.CallMetadata callMetadata,
                             @NonNull WebRtcData.AnswerMetadata answerMetadata,
                             @NonNull WebRtcData.ReceivedAnswerMetadata receivedAnswerMetadata)
  {
    process((s, p) -> p.handleReceivedAnswer(s, callMetadata, answerMetadata, receivedAnswerMetadata));
  }

  public void receivedIceCandidates(@NonNull WebRtcData.CallMetadata callMetadata, @NonNull List<byte[]> iceCandidates) {
    process((s, p) -> p.handleReceivedIceCandidates(s, callMetadata, iceCandidates));
  }

  public void receivedCallHangup(@NonNull WebRtcData.CallMetadata callMetadata, @NonNull WebRtcData.HangupMetadata hangupMetadata) {
    process((s, p) -> p.handleReceivedHangup(s, callMetadata, hangupMetadata));
  }

  public void receivedCallBusy(@NonNull WebRtcData.CallMetadata callMetadata) {
    process((s, p) -> p.handleReceivedBusy(s, callMetadata));
  }

  public void receivedOpaqueMessage(@NonNull WebRtcData.OpaqueMessageMetadata opaqueMessageMetadata) {
    process((s, p) -> p.handleReceivedOpaqueMessage(s, opaqueMessageMetadata));
  }

  public void setRingGroup(boolean ringGroup) {
    process((s, p) -> p.handleSetRingGroup(s, ringGroup));
  }

  private void receivedGroupCallPeekForRingingCheck(@NonNull GroupCallRingCheckInfo groupCallRingCheckInfo, long deviceCount) {
    process((s, p) -> p.handleReceivedGroupCallPeekForRingingCheck(s, groupCallRingCheckInfo, deviceCount));
  }

  public void onAudioDeviceChanged(@NonNull SignalAudioManager.AudioDevice activeDevice, @NonNull Set<SignalAudioManager.AudioDevice> availableDevices) {
    process((s, p) -> p.handleAudioDeviceChanged(s, activeDevice, availableDevices));
  }

  public void onBluetoothPermissionDenied() {
    process((s, p) -> p.handleBluetoothPermissionDenied(s));
  }

  public void selectAudioDevice(@NonNull SignalAudioManager.AudioDevice desiredDevice) {
    process((s, p) -> p.handleSetUserAudioDevice(s, desiredDevice));
  }

  public void setTelecomApproved(long callId, @NonNull RecipientId recipientId) {
    process((s, p) -> p.handleSetTelecomApproved(s, callId, recipientId));
  }

  public void dropCall(long callId) {
    process((s, p) -> p.handleDropCall(s, callId));
  }

  public void peekGroupCall(@NonNull RecipientId id) {
    if (callManager == null) {
      Log.i(TAG, "Unable to peekGroupCall, call manager is null");
      return;
    }

    networkExecutor.execute(() -> {
      try {
        Recipient               group      = Recipient.resolved(id);
        GroupId.V2              groupId    = group.requireGroupId().requireV2();
        GroupExternalCredential credential = GroupManager.getGroupExternalCredential(context, groupId);

        List<GroupCall.GroupMemberInfo> members = Stream.of(GroupManager.getUuidCipherTexts(context, groupId))
                                                        .map(entry -> new GroupCall.GroupMemberInfo(entry.getKey(), entry.getValue().serialize()))
                                                        .toList();

        callManager.peekGroupCall(SignalStore.internalValues().groupCallingServer(), credential.getTokenBytes().toByteArray(), members, peekInfo -> {
          Long threadId = SignalDatabase.threads().getThreadIdFor(group.getId());

          if (threadId != null) {
            SignalDatabase.sms()
                          .updatePreviousGroupCall(threadId,
                                                   peekInfo.getEraId(),
                                                   peekInfo.getJoinedMembers(),
                                                   WebRtcUtil.isCallFull(peekInfo));

            ApplicationDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(threadId), true, 0, BubbleUtil.BubbleState.HIDDEN);

            EventBus.getDefault().postSticky(new GroupCallPeekEvent(id, peekInfo.getEraId(), peekInfo.getDeviceCount(), peekInfo.getMaxDevices()));
          }
        });
      } catch (IOException | VerificationFailedException | CallException e) {
        Log.e(TAG, "error peeking from active conversation", e);
      }
    });
  }

  public void peekGroupCallForRingingCheck(@NonNull GroupCallRingCheckInfo info) {
    if (callManager == null) {
      Log.i(TAG, "Unable to peekGroupCall, call manager is null");
      return;
    }

    networkExecutor.execute(() -> {
      try {
        Recipient               group      = Recipient.resolved(info.getRecipientId());
        GroupId.V2              groupId    = group.requireGroupId().requireV2();
        GroupExternalCredential credential = GroupManager.getGroupExternalCredential(context, groupId);

        List<GroupCall.GroupMemberInfo> members = GroupManager.getUuidCipherTexts(context, groupId)
                                                              .entrySet()
                                                              .stream()
                                                              .map(entry -> new GroupCall.GroupMemberInfo(entry.getKey(), entry.getValue().serialize()))
                                                              .collect(Collectors.toList());

        callManager.peekGroupCall(SignalStore.internalValues().groupCallingServer(), credential.getTokenBytes().toByteArray(), members, peekInfo -> {
          receivedGroupCallPeekForRingingCheck(info, peekInfo.getDeviceCount());
        });
      } catch (IOException | VerificationFailedException | CallException e) {
        Log.e(TAG, "error peeking for ringing check", e);
      }
    });
  }

  void requestGroupMembershipToken(@NonNull GroupId.V2 groupId, int groupCallHashCode) {
    networkExecutor.execute(() -> {
      try {
        GroupExternalCredential credential = GroupManager.getGroupExternalCredential(context, groupId);
        process((s, p) -> p.handleGroupMembershipProofResponse(s, groupCallHashCode, credential.getTokenBytes().toByteArray()));
      } catch (IOException e) {
        Log.w(TAG, "Unable to get group membership proof from service", e);
        process((s, p) -> p.handleGroupCallEnded(s, groupCallHashCode, GroupCall.GroupCallEndReason.SFU_CLIENT_FAILED_TO_JOIN));
      } catch (VerificationFailedException e) {
        Log.w(TAG, "Unable to verify group membership proof", e);
        process((s, p) -> p.handleGroupCallEnded(s, groupCallHashCode, GroupCall.GroupCallEndReason.DEVICE_EXPLICITLY_DISCONNECTED));
      }
    });
  }

  public boolean startCallCardActivityIfPossible() {
    if (Build.VERSION.SDK_INT >= 29 && !ApplicationDependencies.getAppForegroundObserver().isForegrounded()) {
      return false;
    }

    context.startActivity(new Intent(context, WebRtcCallActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    return true;
  }

  @Override
  public void onStartCall(@Nullable Remote remote,
                          @NonNull CallId callId,
                          @NonNull Boolean isOutgoing,
                          @Nullable CallManager.CallMediaType callMediaType)
  {
    Log.i(TAG, "onStartCall(): callId: " + callId + ", outgoing: " + isOutgoing + ", type: " + callMediaType);

    if (callManager == null) {
      Log.w(TAG, "Unable to start call, call manager is not initialized");
      return;
    }

    if (remote == null) {
      return;
    }

    process((s, p) -> {
      RemotePeer remotePeer = (RemotePeer) remote;
      if (s.getCallInfoState().getPeer(remotePeer.hashCode()) == null) {
        Log.w(TAG, "remotePeer not found in map with key: " + remotePeer.hashCode() + "! Dropping.");
        try {
          callManager.drop(callId);
        } catch (CallException e) {
          s = p.callFailure(s, "callManager.drop() failed: ", e);
        }
      }

      remotePeer.setCallId(callId);

      if (isOutgoing) {
        return p.handleStartOutgoingCall(s, remotePeer, WebRtcUtil.getOfferTypeFromCallMediaType(callMediaType));
      } else {
        return p.handleStartIncomingCall(s, remotePeer, WebRtcUtil.getOfferTypeFromCallMediaType(callMediaType));
      }
    });
  }

  @Override
  public void onCallEvent(@Nullable Remote remote, @NonNull CallManager.CallEvent event) {
    if (callManager == null) {
      Log.w(TAG, "Unable to process call event, call manager is not initialized");
      return;
    }

    if (!(remote instanceof RemotePeer)) {
      return;
    }

    process((s, p) -> {
      RemotePeer remotePeer = (RemotePeer) remote;
      if (s.getCallInfoState().getPeer(remotePeer.hashCode()) == null) {
        Log.w(TAG, "remotePeer not found in map with key: " + remotePeer.hashCode() + "! Dropping.");
        try {
          callManager.drop(remotePeer.getCallId());
        } catch (CallException e) {
          return p.callFailure(s, "callManager.drop() failed: ", e);
        }
        return s;
      }

      Log.i(TAG, "onCallEvent(): call_id: " + remotePeer.getCallId() + ", state: " + remotePeer.getState() + ", event: " + event);

      switch (event) {
        case LOCAL_RINGING:
          return p.handleLocalRinging(s, remotePeer);
        case REMOTE_RINGING:
          return p.handleRemoteRinging(s, remotePeer);
        case RECONNECTING:
        case RECONNECTED:
          return p.handleCallReconnect(s, event);
        case LOCAL_CONNECTED:
        case REMOTE_CONNECTED:
          return p.handleCallConnected(s, remotePeer);
        case REMOTE_VIDEO_ENABLE:
          return p.handleRemoteVideoEnable(s, true);
        case REMOTE_VIDEO_DISABLE:
          return p.handleRemoteVideoEnable(s, false);
        case REMOTE_SHARING_SCREEN_ENABLE:
          return p.handleScreenSharingEnable(s, true);
        case REMOTE_SHARING_SCREEN_DISABLE:
          return p.handleScreenSharingEnable(s, false);
        case ENDED_REMOTE_HANGUP:
        case ENDED_REMOTE_HANGUP_NEED_PERMISSION:
        case ENDED_REMOTE_HANGUP_ACCEPTED:
        case ENDED_REMOTE_HANGUP_BUSY:
        case ENDED_REMOTE_HANGUP_DECLINED:
        case ENDED_REMOTE_BUSY:
        case ENDED_REMOTE_GLARE:
        case ENDED_REMOTE_RECALL:
          return p.handleEndedRemote(s, event, remotePeer);
        case ENDED_TIMEOUT:
        case ENDED_INTERNAL_FAILURE:
        case ENDED_SIGNALING_FAILURE:
        case ENDED_GLARE_HANDLING_FAILURE:
        case ENDED_CONNECTION_FAILURE:
          return p.handleEnded(s, event, remotePeer);
        case RECEIVED_OFFER_EXPIRED:
          return p.handleReceivedOfferExpired(s, remotePeer);
        case RECEIVED_OFFER_WHILE_ACTIVE:
        case RECEIVED_OFFER_WITH_GLARE:
          return p.handleReceivedOfferWhileActive(s, remotePeer);
        case ENDED_LOCAL_HANGUP:
        case ENDED_APP_DROPPED_CALL:
          Log.i(TAG, "Ignoring event: " + event);
          break;
        default:
          throw new AssertionError("Unexpected event: " + event.toString());
      }

      return s;
    });
  }

  @Override public void onNetworkRouteChanged(Remote remote, NetworkRoute networkRoute) {
    process((s, p) -> p.handleNetworkRouteChanged(s, networkRoute));
  }

  @Override 
  public void onAudioLevels(Remote remote, int capturedLevel, int receivedLevel) {
    processStateless(s -> serviceState.getActionProcessor().handleAudioLevelsChanged(serviceState, s, capturedLevel, receivedLevel));
  }

  @Override
  public void onCallConcluded(@Nullable Remote remote) {
    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer remotePeer = (RemotePeer) remote;
    Log.i(TAG, "onCallConcluded: call_id: " + remotePeer.getCallId());
    process((s, p) -> p.handleCallConcluded(s, remotePeer));
  }

  @Override
  public void onSendOffer(@NonNull CallId callId,
                          @Nullable Remote remote,
                          @NonNull Integer remoteDevice,
                          @NonNull Boolean broadcast,
                          @NonNull byte[] opaque,
                          @NonNull CallManager.CallMediaType callMediaType)
  {
    if (!(remote instanceof RemotePeer)) {
      return;
    }
    RemotePeer remotePeer = (RemotePeer) remote;

    Log.i(TAG, "onSendOffer: id: " + remotePeer.getCallId().format(remoteDevice) + " type: " + callMediaType.name());

    OfferMessage.Type        offerType     = WebRtcUtil.getOfferTypeFromCallMediaType(callMediaType);
    WebRtcData.CallMetadata  callMetadata  = new WebRtcData.CallMetadata(remotePeer, remoteDevice);
    WebRtcData.OfferMetadata offerMetadata = new WebRtcData.OfferMetadata(opaque, null, offerType);

    process((s, p) -> p.handleSendOffer(s, callMetadata, offerMetadata, broadcast));
  }

  @Override
  public void onSendAnswer(@NonNull CallId callId,
                           @Nullable Remote remote,
                           @NonNull Integer remoteDevice,
                           @NonNull Boolean broadcast,
                           @NonNull byte[] opaque)
  {
    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer remotePeer = (RemotePeer) remote;

    Log.i(TAG, "onSendAnswer: id: " + remotePeer.getCallId().format(remoteDevice));

    WebRtcData.CallMetadata   callMetadata   = new WebRtcData.CallMetadata(remotePeer, remoteDevice);
    WebRtcData.AnswerMetadata answerMetadata = new WebRtcData.AnswerMetadata(opaque, null);

    process((s, p) -> p.handleSendAnswer(s, callMetadata, answerMetadata, broadcast));
  }

  @Override
  public void onSendIceCandidates(@NonNull CallId callId,
                                  @Nullable Remote remote,
                                  @NonNull Integer remoteDevice,
                                  @NonNull Boolean broadcast,
                                  @NonNull List<byte[]> iceCandidates)
  {
    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer remotePeer = (RemotePeer) remote;

    Log.i(TAG, "onSendIceCandidates: id: " + remotePeer.getCallId().format(remoteDevice));

    WebRtcData.CallMetadata callMetadata = new WebRtcData.CallMetadata(remotePeer, remoteDevice);

    process((s, p) -> p.handleSendIceCandidates(s, callMetadata, broadcast, iceCandidates));
  }

  @Override
  public void onSendHangup(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast, @NonNull CallManager.HangupType hangupType, @NonNull Integer deviceId) {
    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer remotePeer = (RemotePeer) remote;

    Log.i(TAG, "onSendHangup: id: " + remotePeer.getCallId().format(remoteDevice) + " type: " + hangupType.name());

    WebRtcData.CallMetadata   callMetadata   = new WebRtcData.CallMetadata(remotePeer, remoteDevice);
    WebRtcData.HangupMetadata hangupMetadata = new WebRtcData.HangupMetadata(WebRtcUtil.getHangupTypeFromCallHangupType(hangupType), false, deviceId);

    process((s, p) -> p.handleSendHangup(s, callMetadata, hangupMetadata, broadcast));
  }

  @Override
  public void onSendBusy(@NonNull CallId callId, @Nullable Remote remote, @NonNull Integer remoteDevice, @NonNull Boolean broadcast) {
    if (!(remote instanceof RemotePeer)) {
      return;
    }

    RemotePeer remotePeer = (RemotePeer) remote;

    Log.i(TAG, "onSendBusy: id: " + remotePeer.getCallId().format(remoteDevice));

    WebRtcData.CallMetadata callMetadata = new WebRtcData.CallMetadata(remotePeer, remoteDevice);

    process((s, p) -> p.handleSendBusy(s, callMetadata, broadcast));
  }

  @Override
  public void onSendCallMessage(@NonNull UUID uuid, @NonNull byte[] bytes, @NonNull CallManager.CallMessageUrgency urgency) {
    Log.i(TAG, "onSendCallMessage():");

    OpaqueMessage            opaqueMessage = new OpaqueMessage(bytes, getUrgencyFromCallUrgency(urgency));
    SignalServiceCallMessage callMessage   = SignalServiceCallMessage.forOpaque(opaqueMessage, true, null);

    networkExecutor.execute(() -> {
      Recipient recipient = Recipient.resolved(RecipientId.from(ServiceId.from(uuid)));
      if (recipient.isBlocked()) {
        return;
      }
      try {
        ApplicationDependencies.getSignalServiceMessageSender()
                               .sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                                                recipient.isSelf() ? Optional.empty() : UnidentifiedAccessUtil.getAccessFor(context, recipient),
                                                callMessage);
      } catch (UntrustedIdentityException e) {
        Log.i(TAG, "sendOpaqueCallMessage onFailure: ", e);
        RetrieveProfileJob.enqueue(recipient.getId());
        process((s, p) -> p.handleGroupMessageSentError(s, Collections.singletonList(recipient.getId()), UNTRUSTED_IDENTITY));
      } catch (IOException e) {
        Log.i(TAG, "sendOpaqueCallMessage onFailure: ", e);
        process((s, p) -> p.handleGroupMessageSentError(s, Collections.singletonList(recipient.getId()), NETWORK_FAILURE));
      }
    });
  }

  @Override
  public void onSendCallMessageToGroup(@NonNull byte[] groupIdBytes, @NonNull byte[] message, @NonNull CallManager.CallMessageUrgency urgency) {
    Log.i(TAG, "onSendCallMessageToGroup():");

    networkExecutor.execute(() -> {
      try {
        GroupId         groupId    = GroupId.v2(new GroupIdentifier(groupIdBytes));
        List<Recipient> recipients = SignalDatabase.groups().getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

        recipients = RecipientUtil.getEligibleForSending((recipients.stream()
                                                                    .map(Recipient::resolve)
                                                                    .collect(Collectors.toList())));

        OpaqueMessage            opaqueMessage = new OpaqueMessage(message, getUrgencyFromCallUrgency(urgency));
        SignalServiceCallMessage callMessage   = SignalServiceCallMessage.forOutgoingGroupOpaque(groupId.getDecodedId(), System.currentTimeMillis(), opaqueMessage, true, null);
        RecipientAccessList      accessList    = new RecipientAccessList(recipients);

        List<SendMessageResult> results = GroupSendUtil.sendCallMessage(context,
                                                                        groupId.requireV2(),
                                                                        recipients,
                                                                        callMessage);

        Set<RecipientId> identifyFailureRecipientIds = results.stream()
                                                              .filter(result -> result.getIdentityFailure() != null)
                                                              .map(result -> accessList.requireIdByAddress(result.getAddress()))
                                                              .collect(Collectors.toSet());

        if (Util.hasItems(identifyFailureRecipientIds)) {
          process((s, p) -> p.handleGroupMessageSentError(s, identifyFailureRecipientIds, UNTRUSTED_IDENTITY));

          RetrieveProfileJob.enqueue(identifyFailureRecipientIds);
        }
      } catch (UntrustedIdentityException | IOException | InvalidInputException e) {
        Log.w(TAG, "onSendCallMessageToGroup failed", e);
      }
    });
  }

  @Override
  public void onSendHttpRequest(long requestId, @NonNull String url, @NonNull CallManager.HttpMethod httpMethod, @Nullable List<HttpHeader> headers, @Nullable byte[] body) {
    if (callManager == null) {
      Log.w(TAG, "Unable to send http request, call manager is not initialized");
      return;
    }

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

      CallingResponse response = ApplicationDependencies.getSignalServiceMessageSender()
                                                        .makeCallingRequest(requestId, url, httpMethod.name(), headerPairs, body);

      try {
        if (response instanceof CallingResponse.Success) {
          CallingResponse.Success success = (CallingResponse.Success) response;
          callManager.receivedHttpResponse(requestId, success.getResponseStatus(), success.getResponseBody());
        } else {
          callManager.httpRequestFailed(requestId);
        }
      } catch (CallException e) {
        Log.i(TAG, "Failed to process HTTP response/failure", e);
      }
    });
  }

  @Override
  public void onGroupCallRingUpdate(@NonNull byte[] groupIdBytes, long ringId, @NonNull UUID uuid, @NonNull CallManager.RingUpdate ringUpdate) {
    try {
      GroupId.V2                groupId = GroupId.v2(new GroupIdentifier(groupIdBytes));
      GroupDatabase.GroupRecord group   = SignalDatabase.groups().getGroup(groupId).orElse(null);

      if (group != null && group.isActive() && !Recipient.resolved(group.getRecipientId()).isBlocked()) {
        process((s, p) -> p.handleGroupCallRingUpdate(s, new RemotePeer(group.getRecipientId()), groupId, ringId, uuid, ringUpdate));
      } else {
        Log.w(TAG, "Unable to ring unknown/inactive/blocked group.");
      }
    } catch (InvalidInputException e) {
      Log.w(TAG, "Unable to ring group due to invalid group id", e);
    }
  }

  @Override
  public void requestMembershipProof(@NonNull final GroupCall groupCall) {
    Log.i(TAG, "requestMembershipProof():");
    process((s, p) -> p.handleGroupRequestMembershipProof(s, groupCall.hashCode()));
  }

  @Override
  public void requestGroupMembers(@NonNull GroupCall groupCall) {
    process((s, p) -> p.handleGroupRequestUpdateMembers(s));
  }

  @Override
  public void onLocalDeviceStateChanged(@NonNull GroupCall groupCall) {
    Log.i(TAG, "onLocalDeviceStateChanged: localAdapterType: " + groupCall.getLocalDeviceState().getNetworkRoute().getLocalAdapterType());
    process((s, p) -> p.handleGroupLocalDeviceStateChanged(s));
  }

  @Override
  public void onAudioLevels(@NonNull GroupCall groupCall) {
    processStateless(s -> serviceState.getActionProcessor().handleGroupAudioLevelsChanged(serviceState, s));
  }

  @Override
  public void onRemoteDeviceStatesChanged(@NonNull GroupCall groupCall) {
    process((s, p) -> p.handleGroupRemoteDeviceStateChanged(s));
  }

  @Override
  public void onPeekChanged(@NonNull GroupCall groupCall) {
    process((s, p) -> p.handleGroupJoinedMembershipChanged(s));
  }

  @Override
  public void onEnded(@NonNull GroupCall groupCall, @NonNull GroupCall.GroupCallEndReason groupCallEndReason) {
    process((s, p) -> p.handleGroupCallEnded(s, groupCall.hashCode(), groupCallEndReason));
  }

  @Override
  public void onFullyInitialized() {
    process((s, p) -> p.handleOrientationChanged(s, s.getLocalDeviceState().isLandscapeEnabled(), s.getLocalDeviceState().getDeviceOrientation().getDegrees()));
  }

  @Override
  public void onCameraSwitchCompleted(@NonNull final CameraState newCameraState) {
    process((s, p) -> p.handleCameraSwitchCompleted(s, newCameraState));
  }

  @Override
  public void onForeground() {
    process((s, p) -> {
      WebRtcViewModel.State          callState      = s.getCallInfoState().getCallState();
      WebRtcViewModel.GroupCallState groupCallState = s.getCallInfoState().getGroupCallState();

      if (callState == CALL_INCOMING && (groupCallState == IDLE || groupCallState.isRinging())) {
        Log.i(TAG, "Starting call activity from foreground listener");
        startCallCardActivityIfPossible();
      }
      ApplicationDependencies.getAppForegroundObserver().removeListener(this);
      return s;
    });
  }

  public void insertMissedCall(@NonNull RemotePeer remotePeer, boolean signal, long timestamp, boolean isVideoOffer) {
    Pair<Long, Long> messageAndThreadId = SignalDatabase.sms().insertMissedCall(remotePeer.getId(), timestamp, isVideoOffer);

    ApplicationDependencies.getMessageNotifier()
                           .updateNotification(context, ConversationId.forConversation(messageAndThreadId.second()), signal);
  }

  public void insertReceivedCall(@NonNull RemotePeer remotePeer, boolean signal, boolean isVideoOffer) {
    Pair<Long, Long> messageAndThreadId = SignalDatabase.sms().insertReceivedCall(remotePeer.getId(), isVideoOffer);

    ApplicationDependencies.getMessageNotifier()
                           .updateNotification(context, ConversationId.forConversation(messageAndThreadId.second()), signal);
  }

  public void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    networkExecutor.execute(() -> {
      try {
        TurnServerInfo turnServerInfo = ApplicationDependencies.getSignalServiceAccountManager().getTurnServerInfo();

        List<PeerConnection.IceServer> iceServers = new LinkedList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        for (String url : turnServerInfo.getUrls()) {
          Log.i(TAG, "ice_server: " + url);
          if (url.startsWith("turn")) {
            iceServers.add(PeerConnection.IceServer.builder(url)
                                                   .setUsername(turnServerInfo.getUsername())
                                                   .setPassword(turnServerInfo.getPassword())
                                                   .createIceServer());
          } else {
            iceServers.add(PeerConnection.IceServer.builder(url).createIceServer());
          }
        }

        process((s, p) -> {
          RemotePeer activePeer = s.getCallInfoState().getActivePeer();
          if (activePeer != null && activePeer.getCallId().equals(remotePeer.getCallId())) {
            return p.handleTurnServerUpdate(s, iceServers, TextSecurePreferences.isTurnOnly(context));
          }

          Log.w(TAG, "Ignoring received turn servers for incorrect call id. requesting_call_id: " + remotePeer.getCallId() + " current_call_id: " + (activePeer != null ? activePeer.getCallId() : "null"));
          return s;
        });
      } catch (IOException e) {
        Log.w(TAG, "Unable to retrieve turn servers: ", e);
        process((s, p) -> p.handleSetupFailure(s, remotePeer.getCallId()));
      }
    });
  }

  public void sendGroupCallUpdateMessage(@NonNull Recipient recipient, @Nullable String groupCallEraId) {
    SignalExecutors.BOUNDED.execute(() -> ApplicationDependencies.getJobManager().add(GroupCallUpdateSendJob.create(recipient.getId(), groupCallEraId)));
  }

  public void updateGroupCallUpdateMessage(@NonNull RecipientId groupId, @Nullable String groupCallEraId, @NonNull Collection<UUID> joinedMembers, boolean isCallFull) {
    SignalExecutors.BOUNDED.execute(() -> SignalDatabase.sms().insertOrUpdateGroupCall(groupId,
                                                                                       Recipient.self().getId(),
                                                                                       System.currentTimeMillis(),
                                                                                       groupCallEraId,
                                                                                       joinedMembers,
                                                                                       isCallFull));
  }

  public void sendCallMessage(@NonNull final RemotePeer remotePeer,
                              @NonNull final SignalServiceCallMessage callMessage)
  {
    networkExecutor.execute(() -> {
      Recipient recipient = Recipient.resolved(remotePeer.getId());
      if (recipient.isBlocked()) {
        return;
      }

      try {
        ApplicationDependencies.getSignalServiceMessageSender()
                               .sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                                                UnidentifiedAccessUtil.getAccessFor(context, recipient),
                                                callMessage);
        process((s, p) -> p.handleMessageSentSuccess(s, remotePeer.getCallId()));
      } catch (UntrustedIdentityException e) {
        RetrieveProfileJob.enqueue(remotePeer.getId());
        processSendMessageFailureWithChangeDetection(remotePeer,
                                                     (s, p) -> p.handleMessageSentError(s,
                                                                                        remotePeer.getCallId(),
                                                                                        UNTRUSTED_IDENTITY,
                                                                                        Optional.ofNullable(e.getIdentityKey())));
      } catch (IOException e) {
        processSendMessageFailureWithChangeDetection(remotePeer,
                                                     (s, p) -> p.handleMessageSentError(s,
                                                                                        remotePeer.getCallId(),
                                                                                        e instanceof UnregisteredUserException ? NO_SUCH_USER : NETWORK_FAILURE,
                                                                                        Optional.empty()));
      }
    });
  }

  private void processSendMessageFailureWithChangeDetection(@NonNull RemotePeer remotePeer,
                                                            @NonNull ProcessAction failureProcessAction)
  {
    process((s, p) -> {
      RemotePeer activePeer = s.getCallInfoState().getActivePeer();

      boolean stateChanged = activePeer == null ||
                             remotePeer.getState() != activePeer.getState() ||
                             !remotePeer.getCallId().equals(activePeer.getCallId());

      if (stateChanged) {
        return p.handleMessageSentSuccess(s, remotePeer.getCallId());
      } else {
        return failureProcessAction.process(s, p);
      }
    });
  }

  interface ProcessAction {
    @NonNull WebRtcServiceState process(@NonNull WebRtcServiceState currentState, @NonNull WebRtcActionProcessor processor);
  }
}
