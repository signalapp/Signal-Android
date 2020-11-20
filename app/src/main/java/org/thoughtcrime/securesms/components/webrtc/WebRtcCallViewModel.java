package org.thoughtcrime.securesms.components.webrtc;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.events.CallParticipant;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

public class WebRtcCallViewModel extends ViewModel {

  private final MutableLiveData<Boolean>               microphoneEnabled  = new MutableLiveData<>(true);
  private final MutableLiveData<Boolean>               isInPipMode        = new MutableLiveData<>(false);
  private final MutableLiveData<WebRtcControls>        webRtcControls     = new MutableLiveData<>(WebRtcControls.NONE);
  private final LiveData<WebRtcControls>               realWebRtcControls = LiveDataUtil.combineLatest(isInPipMode, webRtcControls, this::getRealWebRtcControls);
  private final SingleLiveEvent<Event>                 events             = new SingleLiveEvent<Event>();
  private final MutableLiveData<Long>                  elapsed            = new MutableLiveData<>(-1L);
  private final MutableLiveData<LiveRecipient>         liveRecipient      = new MutableLiveData<>(Recipient.UNKNOWN.live());
  private final MutableLiveData<CallParticipantsState> participantsState  = new MutableLiveData<>(CallParticipantsState.STARTING_STATE);

  private boolean  canDisplayTooltipIfNeeded = true;
  private boolean  hasEnabledLocalVideo      = false;
  private long     callConnectedTime         = -1;
  private Handler  elapsedTimeHandler        = new Handler(Looper.getMainLooper());
  private boolean  answerWithVideoAvailable  = false;
  private Runnable elapsedTimeRunnable       = this::handleTick;
  private boolean  canEnterPipMode           = false;

  private final WebRtcCallRepository repository = new WebRtcCallRepository();

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

  public boolean canEnterPipMode() {
    return canEnterPipMode;
  }

  public boolean isAnswerWithVideoAvailable() {
    return answerWithVideoAvailable;
  }

  @MainThread
  public void setIsInPipMode(boolean isInPipMode) {
    this.isInPipMode.setValue(isInPipMode);

    //noinspection ConstantConditions
    participantsState.setValue(CallParticipantsState.update(participantsState.getValue(), isInPipMode));
  }

  @MainThread
  public void setIsViewingFocusedParticipant(@NonNull CallParticipantsState.SelectedPage page) {
    //noinspection ConstantConditions
    participantsState.setValue(CallParticipantsState.update(participantsState.getValue(), page));
  }

  public void onDismissedVideoTooltip() {
    canDisplayTooltipIfNeeded = false;
  }

  @MainThread
  public void updateFromWebRtcViewModel(@NonNull WebRtcViewModel webRtcViewModel, boolean enableVideo) {
    canEnterPipMode = webRtcViewModel.getState() != WebRtcViewModel.State.CALL_PRE_JOIN;

    CallParticipant localParticipant = webRtcViewModel.getLocalParticipant();

    microphoneEnabled.setValue(localParticipant.isMicrophoneEnabled());

    //noinspection ConstantConditions
    participantsState.setValue(CallParticipantsState.update(participantsState.getValue(), webRtcViewModel, enableVideo));

    updateWebRtcControls(webRtcViewModel.getState(),
                         webRtcViewModel.getGroupState(),
                         localParticipant.getCameraState().isEnabled(),
                         webRtcViewModel.isRemoteVideoEnabled(),
                         webRtcViewModel.isRemoteVideoOffer(),
                         localParticipant.isMoreThanOneCameraAvailable(),
                         webRtcViewModel.isBluetoothAvailable(),
                         Util.hasItems(webRtcViewModel.getRemoteParticipants()),
                         repository.getAudioOutput());

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
      events.setValue(Event.DISMISS_VIDEO_TOOLTIP);
    }

    // If remote video is enabled and we a) haven't shown our video and b) have not dismissed the popup
    if (canDisplayTooltipIfNeeded && webRtcViewModel.isRemoteVideoEnabled() && !hasEnabledLocalVideo) {
      canDisplayTooltipIfNeeded = false;
      events.setValue(Event.SHOW_VIDEO_TOOLTIP);
    }
  }

  private void updateWebRtcControls(@NonNull WebRtcViewModel.State state,
                                    @NonNull WebRtcViewModel.GroupCallState groupState,
                                    boolean isLocalVideoEnabled,
                                    boolean isRemoteVideoEnabled,
                                    boolean isRemoteVideoOffer,
                                    boolean isMoreThanOneCameraAvailable,
                                    boolean isBluetoothAvailable,
                                    boolean hasAtLeastOneRemote,
                                    @NonNull WebRtcAudioOutput audioOutput)
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
        groupCallState = WebRtcControls.GroupCallState.CONNECTING;
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
                                               audioOutput));
  }

  private @NonNull WebRtcControls getRealWebRtcControls(boolean isInPipMode, @NonNull WebRtcControls controls) {
    return isInPipMode ? WebRtcControls.PIP : controls;
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

  public enum Event {
    SHOW_VIDEO_TOOLTIP,
    DISMISS_VIDEO_TOOLTIP
  }
}
