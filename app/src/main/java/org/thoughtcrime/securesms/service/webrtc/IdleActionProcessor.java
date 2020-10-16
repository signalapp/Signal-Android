package org.thoughtcrime.securesms.service.webrtc;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.events.WebRtcViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.RemotePeer;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.webrtc.CapturerObserver;
import org.webrtc.VideoFrame;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;

import java.util.Objects;

/**
 * Action handler for when the system is at rest. Mainly responsible
 * for starting pre-call state, starting an outgoing call, or receiving an
 * incoming call.
 */
public class IdleActionProcessor extends WebRtcActionProcessor {

  private static final String TAG = Log.tag(IdleActionProcessor.class);

  private final BeginCallActionProcessorDelegate beginCallDelegate;

  public IdleActionProcessor(@NonNull WebRtcInteractor webRtcInteractor) {
    super(webRtcInteractor, TAG);
    beginCallDelegate = new BeginCallActionProcessorDelegate(webRtcInteractor, TAG);
  }

  protected @NonNull WebRtcServiceState handleStartIncomingCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handleStartIncomingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleStartIncomingCall(currentState, remotePeer);
  }

  @Override
  protected @NonNull WebRtcServiceState handleOutgoingCall(@NonNull WebRtcServiceState currentState,
                                                           @NonNull RemotePeer remotePeer,
                                                           @NonNull OfferMessage.Type offerType)
  {
    Log.i(TAG, "handleOutgoingCall():");

    currentState = WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState);
    return beginCallDelegate.handleOutgoingCall(currentState, remotePeer, offerType);
  }

  @Override
  protected @NonNull WebRtcServiceState handlePreJoinCall(@NonNull WebRtcServiceState currentState, @NonNull RemotePeer remotePeer) {
    Log.i(TAG, "handlePreJoinCall():");

    WebRtcServiceState newState = initializeVanityCamera(WebRtcVideoUtil.initializeVideo(context, webRtcInteractor.getCameraEventListener(), currentState));

    return newState.builder()
                   .actionProcessor(new PreJoinActionProcessor(webRtcInteractor))
                   .changeCallInfoState()
                   .callState(WebRtcViewModel.State.CALL_PRE_JOIN)
                   .callRecipient(remotePeer.getRecipient())
                   .build();
  }

  private @NonNull WebRtcServiceState initializeVanityCamera(@NonNull WebRtcServiceState currentState) {
    Camera             camera = currentState.getVideoState().requireCamera();
    BroadcastVideoSink sink   = currentState.getVideoState().requireLocalSink();

    if (camera.hasCapturer()) {
      camera.initCapturer(new CapturerObserver() {
        @Override
        public void onFrameCaptured(VideoFrame videoFrame) {
          sink.onFrame(videoFrame);
        }

        @Override
        public void onCapturerStarted(boolean success) {}

        @Override
        public void onCapturerStopped() {}
      });
      camera.setEnabled(true);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(camera.getCameraState())
                       .build();
  }
}
