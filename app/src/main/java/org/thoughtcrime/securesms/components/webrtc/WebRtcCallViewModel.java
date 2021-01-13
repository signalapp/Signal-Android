package org.thoughtcrime.securesms.components.webrtc;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.CallParticipantId;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.groups.LiveGroup;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WebRtcCallViewModel extends ViewModel {

  private final MutableLiveData<Boolean>                    microphoneEnabled         = new MutableLiveData<>(true);
  private final MutableLiveData<Boolean>                    isInPipMode               = new MutableLiveData<>(false);
  private final MutableLiveData<WebRtcControls>             webRtcControls            = new MutableLiveData<>(WebRtcControls.NONE);
  private final LiveData<WebRtcControls>                    realWebRtcControls        = LiveDataUtil.combineLatest(isInPipMode, webRtcControls, this::getRealWebRtcControls);
  private final SingleLiveEvent<Event>                      events                    = new SingleLiveEvent<Event>();
  private final MutableLiveData<Long>                       elapsed                   = new MutableLiveData<>(-1L);
  private final MutableLiveData<LiveRecipient>              liveRecipient             = new MutableLiveData<>(Recipient.UNKNOWN.live());
  private final MutableLiveData<CallParticipantsState>      participantsState         = new MutableLiveData<>(CallParticipantsState.STARTING_STATE);
  private final SingleLiveEvent<CallParticipantListUpdate>  callParticipantListUpdate = new SingleLiveEvent<>();
  private final MutableLiveData<Collection<RecipientId>>    identityChangedRecipients = new MutableLiveData<>(Collections.emptyList());
  private final LiveData<SafetyNumberChangeEvent>           safetyNumberChangeEvent   = LiveDataUtil.combineLatest(isInPipMode, identityChangedRecipients, SafetyNumberChangeEvent::new);
  private final LiveData<Recipient>                         groupRecipient            = LiveDataUtil.filter(Transformations.switchMap(liveRecipient, LiveRecipient::getLiveData), Recipient::isActiveGroup);
  private final LiveData<List<GroupMemberEntry.FullMember>> groupMembers              = LiveDataUtil.skip(Transformations.switchMap(groupRecipient, r -> Transformations.distinctUntilChanged(new LiveGroup(r.requireGroupId()).getFullMembers())), 1);
  private final LiveData<Boolean>                           shouldShowSpeakerHint     = Transformations.map(participantsState, this::shouldShowSpeakerHint);

  private boolean               canDisplayTooltipIfNeeded = true;
  private boolean               hasEnabledLocalVideo      = false;
  private long                  callConnectedTime         = -1;
  private Handler               elapsedTimeHandler        = new Handler(Looper.getMainLooper());
  private boolean               answerWithVideoAvailable  = false;
  private Runnable              elapsedTimeRunnable       = this::handleTick;
  private boolean               canEnterPipMode           = false;
  private List<CallParticipant> previousParticipantsList = Collections.emptyList();
  private boolean               callStarting             = false;

  private final WebRtcCallRepository repository = new WebRtcCallRepository(ApplicationDependencies.getApplication());

  public LiveData<Boolean> getMicrophoneEnabled() {
    return Transformations.distinctUntilChanged(microphoneEnabled);
  }

  public LiveData<WebRtcControls> getWebRtcControls() {
    return realWebRtcControls;
  }

  public LiveRecipient getRecipient() {
    return liveRecipient.getValue();
  }

  public void setRecipient(@NonNull Recipient recipient) {
    liveRecipient.setValue(recipient.live());
  }

  public LiveData<Event> getEvents() {
    return events;
  }

  public LiveData<Long> getCallTime() {
    return Transformations.map(elapsed, timeInCall -> callConnectedTime == -1 ? -1 : timeInCall);
  }

  public LiveData<CallParticipantsState> getCallParticipantsState() {
    return participantsState;
  }

  public LiveData<CallParticipantListUpdate> getCallParticipantListUpdate() {
    return callParticipantListUpdate;
  }

  public LiveData<SafetyNumberChangeEvent> getSafetyNumberChangeEvent() {
    return safetyNumberChangeEvent;
  }

  public LiveData<List<GroupMemberEntry.FullMember>> getGroupMembers() {
    return groupMembers;
  }

  public LiveData<Boolean> shouldShowSpeakerHint() {
    return shouldShowSpeakerHint;
  }

  public boolean canEnterPipMode() {
    return canEnterPipMode;
  }

  public boolean isAnswerWithVideoAvailable() {
    return answerWithVideoAvailable;
  }

  public boolean isCallStarting() {
    return callStarting;
  }

  @MainThread
  public void setIsInPipMode(boolean isInPipMode) {
    this.isInPipMode.setValue(isInPipMode);

    //noinspection ConstantConditions
    participantsState.setValue(CallParticipantsState.update(participantsState.getValue(), isInPipMode));
  }

  @MainThread
  public void setIsViewingFocusedParticipant(@NonNull CallParticipantsState.SelectedPage page) {
    if (page == CallParticipantsState.SelectedPage.FOCUSED) {
      SignalStore.tooltips().markGroupCallSpeakerViewSeen();
    }

    //noinspection ConstantConditions
    participantsState.setValue(CallParticipantsState.update(participantsState.getValue(), page));
  }

  public void onLocalPictureInPictureClicked() {
    CallParticipantsState state = participantsState.getValue();
    if (state.getGroupCallState() != WebRtcViewModel.GroupCallState.IDLE) {
      return;
    }

    participantsState.setValue(CallParticipantsState.setExpanded(participantsState.getValue(),
                                                                 state.getLocalRenderState() != WebRtcLocalRenderState.EXPANDED));
  }

  public void onDismissedVideoTooltip() {
    canDisplayTooltipIfNeeded = false;
  }

  @MainThread
  public void updateFromWebRtcViewModel(@NonNull WebRtcViewModel webRtcViewModel, boolean enableVideo) {
    canEnterPipMode = !webRtcViewModel.getState().isPreJoinOrNetworkUnavailable();
    if (callStarting && webRtcViewModel.getState().isPassedPreJoin()) {
      callStarting = false;
    }

    CallParticipant localParticipant = webRtcViewModel.getLocalParticipant();

    microphoneEnabled.setValue(localParticipant.isMicrophoneEnabled());

    //noinspection ConstantConditions
    participantsState.setValue(CallParticipantsState.update(participantsState.getValue(), webRtcViewModel, enableVideo));

    if (webRtcViewModel.getGroupState().isConnected()) {
      if (!containsPlaceholders(previousParticipantsList)) {
        CallParticipantListUpdate update = CallParticipantListUpdate.computeDeltaUpdate(previousParticipantsList, webRtcViewModel.getRemoteParticipants());
        callParticipantListUpdate.setValue(update);
      }

      previousParticipantsList = webRtcViewModel.getRemoteParticipants();

      identityChangedRecipients.setValue(webRtcViewModel.getIdentityChangedParticipants());
    }

    updateWebRtcControls(webRtcViewModel.getState(),
                         webRtcViewModel.getGroupState(),
                         localParticipant.getCameraState().isEnabled(),
                         webRtcViewModel.isRemoteVideoEnabled(),
                         webRtcViewModel.isRemoteVideoOffer(),
                         localParticipant.isMoreThanOneCameraAvailable(),
                         webRtcViewModel.isBluetoothAvailable(),
                         Util.hasItems(webRtcViewModel.getRemoteParticipants()),
                         repository.getAudioOutput(),
                         webRtcViewModel.getRemoteDevicesCount().orElse(0),
                         webRtcViewModel.getParticipantLimit());

    if (webRtcViewModel.getState() == WebRtcViewModel.State.CALL_CONNECTED && callConnectedTime == -1) {
      callConnectedTime = webRtcViewModel.getCallConnectedTime();
      startTimer();
    } else if (webRtcViewModel.getState() != WebRtcViewModel.State.CALL_CONNECTED || webRtcViewModel.getGroupState().isNotIdleOrConnected()) {
      cancelTimer();
      callConnectedTime = -1;
    }

    if (localParticipant.getCameraState().isEnabled()) {
      canDisplayTooltipIfNeeded = false;
      hasEnabledLocalVideo      = true;
      events.setValue(new Event.DismissVideoTooltip());
    }

    // If remote video is enabled and we a) haven't shown our video and b) have not dismissed the popup
    if (canDisplayTooltipIfNeeded && webRtcViewModel.isRemoteVideoEnabled() && !hasEnabledLocalVideo) {
      canDisplayTooltipIfNeeded = false;
      events.setValue(new Event.ShowVideoTooltip());
    }
  }

  private boolean containsPlaceholders(@NonNull List<CallParticipant> callParticipants) {
    return Stream.of(callParticipants).anyMatch(p -> p.getCallParticipantId().getDemuxId() == CallParticipantId.DEFAULT_ID);
  }

  private void updateWebRtcControls(@NonNull WebRtcViewModel.State state,
                                    @NonNull WebRtcViewModel.GroupCallState groupState,
                                    boolean isLocalVideoEnabled,
                                    boolean isRemoteVideoEnabled,
                                    boolean isRemoteVideoOffer,
                                    boolean isMoreThanOneCameraAvailable,
                                    boolean isBluetoothAvailable,
                                    boolean hasAtLeastOneRemote,
                                    @NonNull WebRtcAudioOutput audioOutput,
                                    long remoteDevicesCount,
                                    @Nullable Long participantLimit)
  {
    final WebRtcControls.CallState callState;

    switch (state) {
      case CALL_PRE_JOIN:
        callState = WebRtcControls.CallState.PRE_JOIN;
        break;
      case CALL_INCOMING:
        callState = WebRtcControls.CallState.INCOMING;
        answerWithVideoAvailable = isRemoteVideoOffer;
        break;
      case CALL_OUTGOING:
      case CALL_RINGING:
        callState = WebRtcControls.CallState.OUTGOING;
        break;
      case CALL_ACCEPTED_ELSEWHERE:
      case CALL_DECLINED_ELSEWHERE:
      case CALL_ONGOING_ELSEWHERE:
      case CALL_NEEDS_PERMISSION:
      case CALL_BUSY:
      case CALL_DISCONNECTED:
        callState = WebRtcControls.CallState.ENDING;
        break;
      case NETWORK_FAILURE:
        callState = WebRtcControls.CallState.ERROR;
        break;
      default:
        callState = WebRtcControls.CallState.ONGOING;
    }

    final WebRtcControls.GroupCallState groupCallState;

    switch (groupState) {
      case DISCONNECTED:
        groupCallState = WebRtcControls.GroupCallState.DISCONNECTED;
        break;
      case CONNECTING:
      case RECONNECTING:
        groupCallState = (participantLimit == null || remoteDevicesCount < participantLimit) ? WebRtcControls.GroupCallState.CONNECTING
                                                                                             : WebRtcControls.GroupCallState.FULL;
        break;
      case CONNECTED:
      case CONNECTED_AND_JOINING:
      case CONNECTED_AND_JOINED:
        groupCallState = WebRtcControls.GroupCallState.CONNECTED;
        break;
      default:
        groupCallState = WebRtcControls.GroupCallState.NONE;
        break;
    }

    webRtcControls.setValue(new WebRtcControls(isLocalVideoEnabled,
                                               isRemoteVideoEnabled || isRemoteVideoOffer,
                                               isMoreThanOneCameraAvailable,
                                               isBluetoothAvailable,
                                               Boolean.TRUE.equals(isInPipMode.getValue()),
                                               hasAtLeastOneRemote,
                                               callState,
                                               groupCallState,
                                               audioOutput,
                                               participantLimit));
  }

  private @NonNull WebRtcControls getRealWebRtcControls(boolean isInPipMode, @NonNull WebRtcControls controls) {
    return isInPipMode ? WebRtcControls.PIP : controls;
  }

  private boolean shouldShowSpeakerHint(@NonNull CallParticipantsState state) {
    return !state.isInPipMode()                        &&
           state.getRemoteDevicesCount().orElse(0) > 1 &&
           state.getGroupCallState().isConnected()     &&
           !SignalStore.tooltips().hasSeenGroupCallSpeakerView();
  }

  private void startTimer() {
    cancelTimer();

    elapsedTimeHandler.post(elapsedTimeRunnable);
  }

  private void handleTick() {
    if (callConnectedTime == -1) {
      return;
    }

    long newValue = (System.currentTimeMillis() - callConnectedTime) / 1000;

    elapsed.postValue(newValue);

    elapsedTimeHandler.postDelayed(elapsedTimeRunnable, 1000);
  }

  private void cancelTimer() {
    elapsedTimeHandler.removeCallbacks(elapsedTimeRunnable);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    cancelTimer();
  }

  public void startCall(boolean isVideoCall) {
    callStarting = true;
    Recipient recipient = getRecipient().get();
    if (recipient.isGroup()) {
      repository.getIdentityRecords(recipient, identityRecords -> {
        if (identityRecords.isUntrusted(false) || identityRecords.isUnverified(false)) {
          List<IdentityDatabase.IdentityRecord> records = identityRecords.getUnverifiedRecords();
          records.addAll(identityRecords.getUntrustedRecords());
          events.postValue(new Event.ShowGroupCallSafetyNumberChange(records));
        } else {
          events.postValue(new Event.StartCall(isVideoCall));
        }
      });
    } else {
      events.postValue(new Event.StartCall(isVideoCall));
    }
  }

  public static abstract class Event {
    private Event() {
    }

    public static class ShowVideoTooltip extends Event {
    }

    public static class DismissVideoTooltip extends Event {
    }

    public static class StartCall extends Event {
      private final boolean isVideoCall;

      public StartCall(boolean isVideoCall) {
        this.isVideoCall = isVideoCall;
      }

      public boolean isVideoCall() {
        return isVideoCall;
      }
    }

    public static class ShowGroupCallSafetyNumberChange extends Event {
      private final List<IdentityDatabase.IdentityRecord> identityRecords;

      public ShowGroupCallSafetyNumberChange(@NonNull List<IdentityDatabase.IdentityRecord> identityRecords) {
        this.identityRecords = identityRecords;
      }

      public @NonNull List<IdentityDatabase.IdentityRecord> getIdentityRecords() {
        return identityRecords;
      }
    }
  }

  public static class SafetyNumberChangeEvent {
    private final boolean                 isInPipMode;
    private final Collection<RecipientId> recipientIds;

    private SafetyNumberChangeEvent(boolean isInPipMode, @NonNull Collection<RecipientId> recipientIds) {
      this.isInPipMode  = isInPipMode;
      this.recipientIds = recipientIds;
    }

    public boolean isInPipMode() {
      return isInPipMode;
    }

    public @NonNull Collection<RecipientId> getRecipientIds() {
      return recipientIds;
    }
  }
}
