package org.thoughtcrime.securesms.service.webrtc;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ResultReceiver;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.ListUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.GenericServerPublicParams;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.calllinks.CallLinkAuthCredentialPresentation;
import org.signal.libsignal.zkgroup.calllinks.CallLinkSecretParams;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.ringrtc.CallException;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.CallLinkRootKey;
import org.signal.ringrtc.CallManager;
import org.signal.ringrtc.GroupCall;
import org.signal.ringrtc.GroupCall.Reaction;
import org.signal.ringrtc.HttpHeader;
import org.signal.ringrtc.NetworkRoute;
import org.signal.ringrtc.PeekInfo;
import org.signal.ringrtc.Remote;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.crypto.SealedSenderAccessUtil;
import org.thoughtcrime.securesms.database.CallLinkTable;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.CallSyncEventJob;
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
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId;
import org.thoughtcrime.securesms.service.webrtc.links.SignalCallLinkManager;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcEphemeralState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.RecipientAccessList;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.rx.RxStore;
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder;
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager;
import org.thoughtcrime.securesms.webrtc.locks.LockManager;
import org.webrtc.PeerConnection;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.OpaqueMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.internal.push.SyncMessage;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.jvm.functions.Function1;
import kotlin.text.Charsets;

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

  private final Context         context;
  private final ExecutorService serviceExecutor;
  private final Executor        networkExecutor;
  private final LockManager     lockManager;

  private WebRtcServiceState            serviceState;
  private RxStore<WebRtcEphemeralState> ephemeralStateStore;
  private boolean                       needsToSetSelfUuid = true;

  private RxStore<Map<RecipientId, CallLinkPeekInfo>> linkPeekInfoStore;

  public SignalCallManager(@NonNull Application application) {
    this.context             = application.getApplicationContext();
    this.lockManager         = new LockManager(this.context);
    this.serviceExecutor     = Executors.newSingleThreadExecutor();
    this.networkExecutor     = Executors.newSingleThreadExecutor();
    this.ephemeralStateStore = new RxStore<>(new WebRtcEphemeralState(), Schedulers.from(serviceExecutor));
    this.linkPeekInfoStore   = new RxStore<>(new HashMap<>(), Schedulers.from(serviceExecutor));

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

  public @NonNull Flowable<Map<RecipientId, CallLinkPeekInfo>> getPeekInfoCache() {
    return linkPeekInfoStore.getStateFlowable();
  }

  public @NonNull Map<RecipientId, CallLinkPeekInfo> getPeekInfoSnapshot() {
    return linkPeekInfoStore.getState();
  }

  private void process(@NonNull ProcessAction action) {
    Throwable t      = new Throwable();
    String    caller = t.getStackTrace().length > 1 ? t.getStackTrace()[1].getMethodName() : "unknown";

    if (callManager == null) {
      Log.w(TAG, "Unable to process action, call manager is not initialized");
      return;
    }

    serviceExecutor.execute(() -> {
      if (needsToSetSelfUuid) {
        try {
          callManager.setSelfUuid(SignalStore.account().requireAci().getRawUuid());
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

  public void setEnableVideo(boolean enabled) {
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

  public void dataModeUpdate() {
    process((s, p) -> p.handleDataModeUpdate(s));
  }

  public void screenOff() {
    process((s, p) -> p.handleScreenOffChange(s));
  }

  public void raiseHand(boolean raised) {
    process((s, p) -> p.handleSelfRaiseHand(s, raised));
  }

  public void react(@NonNull String reaction) {
    processStateless(s -> serviceState.getActionProcessor().handleSendGroupReact(serviceState, s, reaction));
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

  private void receivedGroupCallPeekForRingingCheck(@NonNull GroupCallRingCheckInfo groupCallRingCheckInfo, @NonNull PeekInfo peekInfo) {
    process((s, p) -> p.handleReceivedGroupCallPeekForRingingCheck(s, groupCallRingCheckInfo, peekInfo));
  }

  public void onAudioDeviceChanged(@NonNull SignalAudioManager.AudioDevice activeDevice, @NonNull Set<SignalAudioManager.AudioDevice> availableDevices) {
    process((s, p) -> p.handleAudioDeviceChanged(s, activeDevice, availableDevices));
  }

  public void onBluetoothPermissionDenied() {
    process((s, p) -> p.handleBluetoothPermissionDenied(s));
  }

  public void selectAudioDevice(@NonNull SignalAudioManager.ChosenAudioDeviceIdentifier desiredDevice) {
    process((s, p) -> p.handleSetUserAudioDevice(s, desiredDevice));
  }

  public void setTelecomApproved(long callId, @NonNull RecipientId recipientId) {
    process((s, p) -> p.handleSetTelecomApproved(s, callId, recipientId));
  }

  public void dropCall(long callId) {
    process((s, p) -> p.handleDropCall(s, callId));
  }

  public void setCallLinkJoinRequestAccepted(@NonNull RecipientId participant) {
    process((s, p) -> p.handleSetCallLinkJoinRequestAccepted(s, participant));
  }

  public void setCallLinkJoinRequestRejected(@NonNull RecipientId participant) {
    process((s, p) -> p.handleSetCallLinkJoinRequestRejected(s, participant));
  }

  public void removeFromCallLink(@NonNull CallParticipant participant) {
    process((s, p) -> p.handleRemoveFromCallLink(s, participant));
  }

  public void blockFromCallLink(@NonNull CallParticipant participant) {
    process((s, p) -> p.handleBlockFromCallLink(s, participant));
  }

  public void peekCallLinkCall(@NonNull RecipientId id) {
    if (callManager == null) {
      Log.i(TAG, "Unable to peekCallLinkCall, call manager is null");
      return;
    }

    if (!RemoteConfig.adHocCalling()) {
      Log.i(TAG, "Ad Hoc Calling is disabled. Ignoring request to peek.");
      return;
    }

    networkExecutor.execute(() -> {
      try {
        Recipient              callLinkRecipient = Recipient.resolved(id);
        CallLinkRoomId         callLinkRoomId    = callLinkRecipient.requireCallLinkRoomId();
        CallLinkTable.CallLink callLink          = SignalDatabase.callLinks().getCallLinkByRoomId(callLinkRoomId);

        if (callLink == null || callLink.getCredentials() == null) {
          Log.w(TAG, "Cannot peek call link without credentials.");
          return;
        }

        CallLinkRootKey           callLinkRootKey           = new CallLinkRootKey(callLink.getCredentials().getLinkKeyBytes());
        GenericServerPublicParams genericServerPublicParams = new GenericServerPublicParams(AppDependencies.getSignalServiceNetworkAccess()
                                                                                                           .getConfiguration()
                                                                                                           .getGenericServerPublicParams());


        CallLinkAuthCredentialPresentation callLinkAuthCredentialPresentation = AppDependencies.getGroupsV2Authorization()
                                                                                               .getCallLinkAuthorizationForToday(
                                                                                                           genericServerPublicParams,
                                                                                                           CallLinkSecretParams.deriveFromRootKey(callLinkRootKey.getKeyBytes())
                                                                                                       );

        callManager.peekCallLinkCall(SignalStore.internal().groupCallingServer(), callLinkAuthCredentialPresentation.serialize(), callLinkRootKey, peekInfo -> {
          PeekInfo info = peekInfo.getValue();
          if (info == null) {
            Log.w(TAG, "Failed to get peek info: " + peekInfo.getStatus());
            return;
          }

          String eraId = info.getEraId();
          if (eraId != null && !info.getJoinedMembers().isEmpty()) {
            if (SignalDatabase.calls().insertAdHocCallFromObserveEvent(callLinkRecipient, System.currentTimeMillis(), eraId)) {
              AppDependencies.getJobManager()
                             .add(CallSyncEventJob.createForObserved(callLinkRecipient.getId(), CallId.fromEra(eraId).longValue()));
            }
          }

          linkPeekInfoStore.update(store -> {
            Map<RecipientId, CallLinkPeekInfo> newHashMap = new HashMap<>(store);
            newHashMap.put(id, CallLinkPeekInfo.fromPeekInfo(info));
            return newHashMap;
          });
        });
      } catch (CallException | VerificationFailedException | InvalidInputException | IOException e) {
        Log.i(TAG, "error peeking call link", e);
      }
    });
  }

  public void peekGroupCall(@NonNull RecipientId id) {
    peekGroupCall(id, null);
  }

  public void peekGroupCall(@NonNull RecipientId id, @Nullable Consumer<PeekInfo> onWillUpdateCallFromPeek) {
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
        callManager.peekGroupCall(SignalStore.internal().groupCallingServer(), credential.token.getBytes(Charsets.UTF_8), members, peekInfo -> {
          Long threadId = SignalDatabase.threads().getThreadIdFor(group.getId());

          if (threadId != null) {
            if (onWillUpdateCallFromPeek != null) {
              onWillUpdateCallFromPeek.accept(peekInfo);
            }

            SignalDatabase.calls()
                          .updateGroupCallFromPeek(threadId,
                                                   peekInfo.getEraId(),
                                                   peekInfo.getJoinedMembers(),
                                                   WebRtcUtil.isCallFull(peekInfo));

            AppDependencies.getMessageNotifier().updateNotification(context, ConversationId.forConversation(threadId));

            EventBus.getDefault().postSticky(new GroupCallPeekEvent(id, peekInfo.getEraId(), peekInfo.getDeviceCount(), peekInfo.getMaxDevices()));
          }
        });
      } catch (IOException | VerificationFailedException | CallException e) {
        Log.i(TAG, "error peeking from active conversation", e);
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

        callManager.peekGroupCall(SignalStore.internal().groupCallingServer(),
                                  credential.token.getBytes(Charsets.UTF_8),
                                  members,
                                  peekInfo -> receivedGroupCallPeekForRingingCheck(info, peekInfo));
      } catch (IOException | VerificationFailedException | CallException e) {
        Log.i(TAG, "error peeking for ringing check", e);
      }
    });
  }

  void requestGroupMembershipToken(@NonNull GroupId.V2 groupId, int groupCallHashCode) {
    networkExecutor.execute(() -> {
      try {
        GroupExternalCredential credential = GroupManager.getGroupExternalCredential(context, groupId);
        process((s, p) -> p.handleGroupMembershipProofResponse(s, groupCallHashCode, credential.token.getBytes(Charsets.UTF_8)));
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
    if (Build.VERSION.SDK_INT >= CallNotificationBuilder.API_LEVEL_CALL_STYLE) {
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
          throw new AssertionError("Unexpected event: " + event);
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
  public void onLowBandwidthForVideo(@Nullable Remote remote, boolean recovered) {
    // TODO: Implement handling of the "low outgoing bandwidth for video" notification.
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
    WebRtcData.OfferMetadata offerMetadata = new WebRtcData.OfferMetadata(opaque, offerType);

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
    WebRtcData.AnswerMetadata answerMetadata = new WebRtcData.AnswerMetadata(opaque);

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
    WebRtcData.HangupMetadata hangupMetadata = new WebRtcData.HangupMetadata(WebRtcUtil.getHangupTypeFromCallHangupType(hangupType), deviceId);

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
  public void onSendCallMessage(@NonNull UUID aciUuid, @NonNull byte[] message, @NonNull CallManager.CallMessageUrgency urgency) {
    Log.i(TAG, "onSendCallMessage():");

    OpaqueMessage            opaqueMessage = new OpaqueMessage(message, getUrgencyFromCallUrgency(urgency));
    SignalServiceCallMessage callMessage   = SignalServiceCallMessage.forOpaque(opaqueMessage, null);

    networkExecutor.execute(() -> {
      Recipient recipient = Recipient.resolved(RecipientId.from(ACI.from(aciUuid)));
      if (recipient.isBlocked()) {
        return;
      }
      try {
        AppDependencies.getSignalServiceMessageSender()
                       .sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                                        recipient.isSelf() ? SealedSenderAccess.NONE : SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
                                        callMessage);
      } catch (UntrustedIdentityException e) {
        Log.i(TAG, "onSendCallMessage onFailure: ", e);
        RetrieveProfileJob.enqueue(recipient.getId());
        process((s, p) -> p.handleGroupMessageSentError(s, Collections.singletonList(recipient.getId()), UNTRUSTED_IDENTITY));
      } catch (IOException e) {
        Log.i(TAG, "onSendCallMessage onFailure: ", e);
        process((s, p) -> p.handleGroupMessageSentError(s, Collections.singletonList(recipient.getId()), NETWORK_FAILURE));
      }
    });
  }

  @Override
  public void onSendCallMessageToGroup(@NonNull byte[] groupIdBytes, @NonNull byte[] message, @NonNull CallManager.CallMessageUrgency urgency, @NonNull List<UUID> overrideRecipients) {
    Log.i(TAG, "onSendCallMessageToGroup():");

    networkExecutor.execute(() -> {
      try {
        GroupId         groupId    = GroupId.v2(new GroupIdentifier(groupIdBytes));
        List<Recipient> recipients = SignalDatabase.groups().getGroupMembers(groupId, GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        Set<UUID>       toInclude  = new HashSet<>(overrideRecipients.size());

        toInclude.addAll(overrideRecipients);

        recipients = RecipientUtil.getEligibleForSending((recipients.stream()
                                                                    .map(Recipient::resolve)
                                                                    .filter(r -> {
                                                                      if (toInclude.isEmpty()) {
                                                                        return true;
                                                                      }
                                                                      return r.getHasServiceId() && toInclude.contains(r.requireServiceId().getRawUuid());
                                                                    })
                                                                    .collect(Collectors.toList())));

        OpaqueMessage            opaqueMessage = new OpaqueMessage(message, getUrgencyFromCallUrgency(urgency));
        SignalServiceCallMessage callMessage   = SignalServiceCallMessage.forOutgoingGroupOpaque(groupId.getDecodedId(), System.currentTimeMillis(), opaqueMessage, null);
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

      CallingResponse response = AppDependencies.getSignalServiceMessageSender()
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
  public void onGroupCallRingUpdate(@NonNull byte[] groupIdBytes, long ringId, @NonNull UUID sender, @NonNull CallManager.RingUpdate ringUpdate) {
    try {
      ACI         senderAci       = ACI.from(sender);
      GroupId.V2  groupId         = GroupId.v2(new GroupIdentifier(groupIdBytes));
      GroupRecord group           = SignalDatabase.groups().getGroup(groupId).orElse(null);
      Recipient   senderRecipient = Recipient.externalPush(senderAci);

      if (group != null &&
          group.isActive() &&
          !Recipient.resolved(group.getRecipientId()).isBlocked() &&
          (!group.isAnnouncementGroup() || group.isAdmin(senderRecipient)))
      {
        process((s, p) -> p.handleGroupCallRingUpdate(s, new RemotePeer(group.getRecipientId()), groupId, ringId, senderAci, ringUpdate));
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
  public void onLowBandwidthForVideo(@NonNull GroupCall groupCall, boolean recovered) {
    // TODO: Implement handling of the "low outgoing bandwidth for video" notification.
  }

  @Override
  public void onReactions(@NonNull GroupCall groupCall, List<Reaction> reactions) {
    processStateless(s -> serviceState.getActionProcessor().handleGroupCallReaction(serviceState, s, reactions));
  }

  @Override
  public void onRaisedHands(@NonNull GroupCall groupCall, List<Long> raisedHands) {
    process((s, p) -> p.handleGroupCallRaisedHand(s, raisedHands));
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
  public void onCameraStopped() {
    Log.i(TAG, "Camera error. Muting video.");
    setEnableVideo(false);
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
      AppDependencies.getAppForegroundObserver().removeListener(this);
      return s;
    });
  }

  public void insertMissedCall(@NonNull RemotePeer remotePeer, long timestamp, boolean isVideoOffer, @NonNull CallTable.Event missedEvent) {
    CallTable.Call call = SignalDatabase.calls()
                                        .updateOneToOneCall(remotePeer.getCallId().longValue(), missedEvent);

    if (call == null) {
      CallTable.Type type = isVideoOffer ? CallTable.Type.VIDEO_CALL : CallTable.Type.AUDIO_CALL;

      SignalDatabase.calls()
                    .insertOneToOneCall(remotePeer.getCallId().longValue(), timestamp, remotePeer.getId(), type, CallTable.Direction.INCOMING, missedEvent);
    }
  }

  public void insertReceivedCall(@NonNull RemotePeer remotePeer, boolean isVideoOffer) {
    CallTable.Call call = SignalDatabase.calls()
                                        .updateOneToOneCall(remotePeer.getCallId().longValue(), CallTable.Event.ACCEPTED);

    if (call == null) {
      CallTable.Type type = isVideoOffer ? CallTable.Type.VIDEO_CALL : CallTable.Type.AUDIO_CALL;

      SignalDatabase.calls()
                    .insertOneToOneCall(remotePeer.getCallId().longValue(), System.currentTimeMillis(), remotePeer.getId(), type, CallTable.Direction.INCOMING, CallTable.Event.ACCEPTED);
    }
  }

  public void retrieveTurnServers(@NonNull RemotePeer remotePeer) {
    networkExecutor.execute(() -> {
      try {
        TurnServerInfo turnServerInfo = AppDependencies.getSignalServiceAccountManager().getTurnServerInfo();

        List<PeerConnection.IceServer> iceServers = new LinkedList<>();
        for (String url : ListUtil.emptyIfNull(turnServerInfo.getUrlsWithIps())) {
          if (url.startsWith("turn")) {
            iceServers.add(PeerConnection.IceServer.builder(url)
                                                   .setUsername(turnServerInfo.getUsername())
                                                   .setPassword(turnServerInfo.getPassword())
                                                   .setHostname(turnServerInfo.getHostname())
                                                   .createIceServer());
          } else {
            iceServers.add(PeerConnection.IceServer.builder(url)
                                                   .setHostname(turnServerInfo.getHostname())
                                                   .createIceServer());
          }
        }
        for (String url : ListUtil.emptyIfNull(turnServerInfo.getUrls())) {
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

  public void sendGroupCallUpdateMessage(@NonNull Recipient recipient, @Nullable String groupCallEraId, final @Nullable CallId callId, boolean isIncoming, boolean isJoinEvent) {
    Log.i(TAG, "sendGroupCallUpdateMessage id: " + recipient.getId() + " era: " + groupCallEraId + " isIncoming: " + isIncoming + " isJoinEvent: " + isJoinEvent);

    if (recipient.isCallLink()) {
      if (isJoinEvent) {
        SignalExecutors.BOUNDED.execute(() -> {
          CallId callIdLocal = callId;

          if (callIdLocal == null && groupCallEraId != null) {
            callIdLocal = CallId.fromEra(groupCallEraId);
          }

          if (callIdLocal != null) {
            AppDependencies.getJobManager().add(
                CallSyncEventJob.createForJoin(
                    recipient.getId(),
                    callIdLocal.longValue(),
                    isIncoming
                )
            );
          }
        });
      } else {
        Log.i(TAG, "sendGroupCallUpdateMessage -- ignoring non-join event for call link");
      }
      return;
    }

    SignalExecutors.BOUNDED.execute(() -> {
      GroupCallUpdateSendJob updateSendJob = GroupCallUpdateSendJob.create(recipient.getId(), groupCallEraId);
      JobManager.Chain       chain         = AppDependencies.getJobManager().startChain(updateSendJob);
      CallId                 callIdLocal   = callId;

      if (callIdLocal == null && groupCallEraId != null) {
        callIdLocal = CallId.fromEra(groupCallEraId);
      }

      if (callIdLocal != null) {
        if (isJoinEvent) {
          chain.then(CallSyncEventJob.createForJoin(
              recipient.getId(),
              callIdLocal.longValue(),
              isIncoming
          ));
        } else if (isIncoming) {
          chain.then(CallSyncEventJob.createForNotAccepted(
              recipient.getId(),
              callIdLocal.longValue(),
              isIncoming
          ));
        }
      } else {
        Log.w(TAG, "Can't send sync message without a call id. isIncoming: " + isIncoming + " isJoinEvent: " + isJoinEvent);
      }

      chain.enqueue();
    });
  }

  public void updateGroupCallUpdateMessage(@NonNull RecipientId groupId, @Nullable String groupCallEraId, @NonNull Collection<UUID> joinedMembers, boolean isCallFull) {
    SignalExecutors.BOUNDED.execute(() -> SignalDatabase.calls().insertOrUpdateGroupCallFromLocalEvent(groupId,
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
        AppDependencies.getSignalServiceMessageSender()
                       .sendCallMessage(RecipientUtil.toSignalServiceAddress(context, recipient),
                                        SealedSenderAccessUtil.getSealedSenderAccessFor(recipient),
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

  public void sendAcceptedCallEventSyncMessage(@NonNull RemotePeer remotePeer, boolean isOutgoing, boolean isVideoCall) {
    SignalDatabase
        .calls()
        .updateOneToOneCall(remotePeer.getCallId().longValue(), CallTable.Event.ACCEPTED);

    if (TextSecurePreferences.isMultiDevice(context)) {
      networkExecutor.execute(() -> {
        try {
          SyncMessage.CallEvent callEvent = CallEventSyncMessageUtil.createAcceptedSyncMessage(remotePeer, System.currentTimeMillis(), isOutgoing, isVideoCall);
          AppDependencies.getSignalServiceMessageSender().sendSyncMessage(SignalServiceSyncMessage.forCallEvent(callEvent));
        } catch (IOException | UntrustedIdentityException e) {
          Log.w(TAG, "Unable to send call event sync message for " + remotePeer.getCallId().longValue(), e);
        }
      });
    }
  }

  public void sendNotAcceptedCallEventSyncMessage(@NonNull RemotePeer remotePeer, boolean isOutgoing, boolean isVideoCall) {
    SignalDatabase
        .calls()
        .updateOneToOneCall(remotePeer.getCallId().longValue(), CallTable.Event.NOT_ACCEPTED);

    if (TextSecurePreferences.isMultiDevice(context)) {
      networkExecutor.execute(() -> {
        try {
          SyncMessage.CallEvent callEvent = CallEventSyncMessageUtil.createNotAcceptedSyncMessage(remotePeer, System.currentTimeMillis(), isOutgoing, isVideoCall);
          AppDependencies.getSignalServiceMessageSender().sendSyncMessage(SignalServiceSyncMessage.forCallEvent(callEvent));
        } catch (IOException | UntrustedIdentityException e) {
          Log.w(TAG, "Unable to send call event sync message for " + remotePeer.getCallId().longValue(), e);
        }
      });
    }
  }

  public void sendGroupCallNotAcceptedCallEventSyncMessage(@NonNull RemotePeer remotePeer, boolean isOutgoing) {
    if (TextSecurePreferences.isMultiDevice(context)) {
      networkExecutor.execute(() -> {
        try {
          SyncMessage.CallEvent callEvent = CallEventSyncMessageUtil.createNotAcceptedSyncMessage(remotePeer, System.currentTimeMillis(), isOutgoing, true);
          AppDependencies.getSignalServiceMessageSender().sendSyncMessage(SignalServiceSyncMessage.forCallEvent(callEvent));
        } catch (IOException | UntrustedIdentityException e) {
          Log.w(TAG, "Unable to send call event sync message for " + remotePeer.getCallId().longValue(), e);
        }
      });
    }
  }

  public @NonNull SignalCallLinkManager getCallLinkManager() {
    return new SignalCallLinkManager(Objects.requireNonNull(callManager));
  }

  public void relaunchPipOnForeground() {
    AppDependencies.getAppForegroundObserver().addListener(new RelaunchListener(AppDependencies.getAppForegroundObserver().isForegrounded()));
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

  private class RelaunchListener implements AppForegroundObserver.Listener {
    private boolean canRelaunch;

    public RelaunchListener(boolean isForegrounded) {
      canRelaunch = !isForegrounded;
    }

    @Override
    public void onForeground() {
      if (canRelaunch) {
        if (isSystemPipEnabledAndAvailable()) {
          process((s, p) -> {
            WebRtcViewModel.State callState = s.getCallInfoState().getCallState();

            if (callState.getInOngoingCall()) {
              Intent intent = new Intent(context, WebRtcCallActivity.class);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              intent.putExtra(WebRtcCallActivity.EXTRA_LAUNCH_IN_PIP, true);
              context.startActivity(intent);
            }

            return s;
          });
        }
        AppDependencies.getAppForegroundObserver().removeListener(this);
      }
    }

    @Override
    public void onBackground() {
      canRelaunch = true;
    }

    private boolean isSystemPipEnabledAndAvailable() {
      return Build.VERSION.SDK_INT >= 26 && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }
  }

  interface ProcessAction {
    @NonNull WebRtcServiceState process(@NonNull WebRtcServiceState currentState, @NonNull WebRtcActionProcessor processor);
  }
}
