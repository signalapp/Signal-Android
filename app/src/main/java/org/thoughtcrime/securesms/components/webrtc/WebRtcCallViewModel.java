package org.thoughtcrime.securesms.components.webrtc;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

public class WebRtcCallViewModel extends ViewModel {

  private final MutableLiveData<Boolean>                remoteVideoEnabled   = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean>                microphoneEnabled    = new MutableLiveData<>(true);
  private final MutableLiveData<WebRtcLocalRenderState> localRenderState     = new MutableLiveData<>(WebRtcLocalRenderState.GONE);
  private final MutableLiveData<Boolean>                isInPipMode          = new MutableLiveData<>(false);
  private final MutableLiveData<Boolean>                localVideoEnabled    = new MutableLiveData<>(false);
  private final MutableLiveData<CameraState.Direction>  cameraDirection      = new MutableLiveData<>(CameraState.Direction.FRONT);
  private final LiveData<Boolean>                       shouldDisplayLocal   = LiveDataUtil.combineLatest(isInPipMode, localVideoEnabled, (a, b) -> !a && b);
  private final LiveData<WebRtcLocalRenderState>        realLocalRenderState = LiveDataUtil.combineLatest(shouldDisplayLocal, localRenderState, this::getRealLocalRenderState);
  private final MutableLiveData<WebRtcControls>         webRtcControls       = new MutableLiveData<>(WebRtcControls.NONE);
  private final LiveData<WebRtcControls>                realWebRtcControls   = LiveDataUtil.combineLatest(isInPipMode, webRtcControls, this::getRealWebRtcControls);
  private final SingleLiveEvent<Event>                  events               = new SingleLiveEvent<Event>();
  private final MutableLiveData<Long>                   ellapsed             = new MutableLiveData<>(-1L);
  private final MutableLiveData<LiveRecipient>          liveRecipient        = new MutableLiveData<>(Recipient.UNKNOWN.live());

  private boolean       canDisplayTooltipIfNeeded = true;
  private boolean       hasEnabledLocalVideo      = false;
  private boolean       showVideoForOutgoing      = false;
  private long          callConnectedTime         = -1;
  private Handler       ellapsedTimeHandler       = new Handler(Looper.getMainLooper());
  private boolean       answerWithVideoAvailable  = false;
  private Runnable      ellapsedTimeRunnable      = this::handleTick;


  private final WebRtcCallRepository repository = new WebRtcCallRepository();

  public LiveData<Boolean> getRemoteVideoEnabled() {
    return Transformations.distinctUntilChanged(remoteVideoEnabled);
  }

  public LiveData<Boolean> getMicrophoneEnabled() {
    return Transformations.distinctUntilChanged(microphoneEnabled);
  }

  public LiveData<CameraState.Direction> getCameraDirection() {
    return Transformations.distinctUntilChanged(cameraDirection);
  }

  public LiveData<Boolean> displaySquareCallCard() {
    return isInPipMode;
  }

  public LiveData<WebRtcLocalRenderState> getLocalRenderState() {
    return realLocalRenderState;
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
    return Transformations.map(ellapsed, timeInCall -> callConnectedTime == -1 ? -1 : timeInCall);
  }

  public boolean isAnswerWithVideoAvailable() {
    return answerWithVideoAvailable;
  }

  @MainThread
  public void setIsInPipMode(boolean isInPipMode) {
    this.isInPipMode.setValue(isInPipMode);
  }

  public void onDismissedVideoTooltip() {
    canDisplayTooltipIfNeeded = false;
  }

  @MainThread
  public void updateFromWebRtcViewModel(@NonNull WebRtcViewModel webRtcViewModel, boolean enableVideo) {
    remoteVideoEnabled.setValue(webRtcViewModel.isRemoteVideoEnabled());
    microphoneEnabled.setValue(webRtcViewModel.isMicrophoneEnabled());

    if (isValidCameraDirectionForUi(webRtcViewModel.getLocalCameraState().getActiveDirection())) {
      cameraDirection.setValue(webRtcViewModel.getLocalCameraState().getActiveDirection());
    }

    localVideoEnabled.setValue(webRtcViewModel.getLocalCameraState().isEnabled());

    if (enableVideo) {
      showVideoForOutgoing = webRtcViewModel.getState() == WebRtcViewModel.State.CALL_OUTGOING;
    } else if (webRtcViewModel.getState() != WebRtcViewModel.State.CALL_OUTGOING) {
      showVideoForOutgoing = false;
    }

    updateLocalRenderState(webRtcViewModel.getState());
    updateWebRtcControls(webRtcViewModel.getState(),
                         webRtcViewModel.getLocalCameraState().isEnabled(),
                         webRtcViewModel.isRemoteVideoEnabled(),
                         webRtcViewModel.isRemoteVideoOffer(),
                         webRtcViewModel.getLocalCameraState().getCameraCount() > 1,
                         webRtcViewModel.isBluetoothAvailable(),
                         repository.getAudioOutput());

    if (webRtcViewModel.getState() == WebRtcViewModel.State.CALL_CONNECTED && callConnectedTime == -1) {
      callConnectedTime = webRtcViewModel.getCallConnectedTime();
      startTimer();
    } else if (webRtcViewModel.getState() != WebRtcViewModel.State.CALL_CONNECTED) {
      cancelTimer();
      callConnectedTime = -1;
    }

    if (webRtcViewModel.getLocalCameraState().isEnabled()) {
      canDisplayTooltipIfNeeded = false;
      hasEnabledLocalVideo = true;
      events.setValue(Event.DISMISS_VIDEO_TOOLTIP);
    }

    // If remote video is enabled and we a) haven't shown our video and b) have not dismissed the popup
    if (canDisplayTooltipIfNeeded && webRtcViewModel.isRemoteVideoEnabled() && !hasEnabledLocalVideo) {
      canDisplayTooltipIfNeeded = false;
      events.setValue(Event.SHOW_VIDEO_TOOLTIP);
    }
  }

  private boolean isValidCameraDirectionForUi(CameraState.Direction direction) {
    return direction == CameraState.Direction.FRONT || direction == CameraState.Direction.BACK;
  }

  private void updateLocalRenderState(WebRtcViewModel.State state) {
    if (state == WebRtcViewModel.State.CALL_CONNECTED) {
      localRenderState.setValue(WebRtcLocalRenderState.SMALL);
    } else {
      localRenderState.setValue(WebRtcLocalRenderState.LARGE);
    }
  }

  private void updateWebRtcControls(WebRtcViewModel.State state,
                                    boolean isLocalVideoEnabled,
                                    boolean isRemoteVideoEnabled,
                                    boolean isRemoteVideoOffer,
                                    boolean isMoreThanOneCameraAvailable,
                                    boolean isBluetoothAvailable,
                                    WebRtcAudioOutput audioOutput)
  {

    final WebRtcControls.CallState callState;

    switch (state) {
      case CALL_INCOMING:
        callState = WebRtcControls.CallState.INCOMING;
        answerWithVideoAvailable = isRemoteVideoOffer;
        break;
      default:
        callState = WebRtcControls.CallState.ONGOING;
    }

    webRtcControls.setValue(new WebRtcControls(isLocalVideoEnabled,
                                               isRemoteVideoEnabled || isRemoteVideoOffer,
                                               isMoreThanOneCameraAvailable,
                                               isBluetoothAvailable,
                                               isInPipMode.getValue() == Boolean.TRUE,
                                               callState,
                                               audioOutput));
  }

  private @NonNull WebRtcLocalRenderState getRealLocalRenderState(boolean shouldDisplayLocalVideo, @NonNull WebRtcLocalRenderState state) {
    if (shouldDisplayLocalVideo || showVideoForOutgoing) return state;
    else                                                 return WebRtcLocalRenderState.GONE;
  }

  private @NonNull WebRtcControls getRealWebRtcControls(boolean isInPipMode, @NonNull WebRtcControls controls) {
    if (isInPipMode) return WebRtcControls.PIP;
    else             return controls;
  }

  private void startTimer() {
    cancelTimer();

    ellapsedTimeHandler.post(ellapsedTimeRunnable);
  }

  private void handleTick() {
    if (callConnectedTime == -1) {
      return;
    }

    long newValue = (System.currentTimeMillis() - callConnectedTime) / 1000;

    ellapsed.postValue(newValue);

    ellapsedTimeHandler.postDelayed(ellapsedTimeRunnable, 1000);
  }

  private void cancelTimer() {
    ellapsedTimeHandler.removeCallbacks(ellapsedTimeRunnable);
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
