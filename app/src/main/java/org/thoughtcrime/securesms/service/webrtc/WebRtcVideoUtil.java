package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.components.webrtc.EglBaseWrapper;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.ringrtc.OutgoingVideoSourceRouter;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.webrtc.CapturerObserver;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Helper for initializing, reinitializing, and deinitializing the outgoing video
 * pipeline (camera + screen share, owned by the {@link OutgoingVideoSourceRouter}).
 */
public final class WebRtcVideoUtil {

  private WebRtcVideoUtil() {}

  public static @NonNull WebRtcServiceState initializeVideo(@NonNull Context context,
                                                            @NonNull CameraEventListener cameraEventListener,
                                                            @NonNull WebRtcServiceState currentState,
                                                            @NonNull Object eglBaseHolder)
  {
    final WebRtcServiceStateBuilder builder = currentState.builder();

    ThreadUtil.runOnMainSync(() -> {
      EglBaseWrapper            eglBase   = EglBaseWrapper.acquireEglBase(eglBaseHolder);
      BroadcastVideoSink        localSink = new BroadcastVideoSink(eglBase,
                                                                   true,
                                                                   false,
                                                                   currentState.getLocalDeviceState().getOrientation().getDegrees());
      OutgoingVideoSourceRouter router    = new OutgoingVideoSourceRouter(context,
                                                                          eglBase,
                                                                          cameraEventListener,
                                                                          CameraState.Direction.FRONT);

      router.setOrientation(currentState.getLocalDeviceState().getOrientation().getDegrees());

      builder.changeVideoState()
             .eglBase(eglBase)
             .localSink(localSink)
             .router(router)
             .commit()
             .changeLocalDeviceState()
             .cameraState(router.getCameraState())
             .commit();
    });

    return builder.build();
  }

  public static @NonNull WebRtcServiceState reinitializeCamera(@NonNull Context context,
                                                               @NonNull CameraEventListener cameraEventListener,
                                                               @NonNull WebRtcServiceState currentState)
  {
    final WebRtcServiceStateBuilder builder = currentState.builder();

    ThreadUtil.runOnMainSync(() -> {
      OutgoingVideoSourceRouter oldRouter = currentState.getVideoState().requireRouter();
      oldRouter.setCameraEventListener(null);
      oldRouter.setEnabled(false);
      oldRouter.dispose();

      OutgoingVideoSourceRouter router = new OutgoingVideoSourceRouter(context,
                                                                       currentState.getVideoState().getLockableEglBase(),
                                                                       cameraEventListener,
                                                                       currentState.getLocalDeviceState().getCameraState().getActiveDirection());

      router.setOrientation(currentState.getLocalDeviceState().getOrientation().getDegrees());

      builder.changeVideoState()
             .router(router)
             .commit()
             .changeLocalDeviceState()
             .cameraState(router.getCameraState())
             .commit();
    });

    return builder.build();
  }

  public static @NonNull WebRtcServiceState deinitializeVideo(@NonNull WebRtcServiceState currentState) {
    OutgoingVideoSourceRouter router = currentState.getVideoState().getRouter();
    if (router != null) {
      router.dispose();
    }

    return currentState.builder()
                       .changeVideoState()
                       .eglBase(null)
                       .router(null)
                       .localSink(null)
                       .commit()
                       .changeLocalDeviceState()
                       .cameraState(CameraState.UNKNOWN)
                       .build();
  }

  public static @NonNull WebRtcServiceState initializeVanityCamera(@NonNull WebRtcServiceState currentState) {
    OutgoingVideoSourceRouter router = currentState.getVideoState().requireRouter();
    VideoSink                 sink   = currentState.getVideoState().requireLocalSink();

    if (router.hasCapturer()) {
      router.initCapturer(new CapturerObserver() {
        @Override
        public void onFrameCaptured(VideoFrame videoFrame) {
          sink.onFrame(videoFrame);
        }

        @Override
        public void onCapturerStarted(boolean success) {}

        @Override
        public void onCapturerStopped() {}
      });
      router.setEnabled(true);
    }

    return currentState.builder()
                       .changeLocalDeviceState()
                       .cameraState(router.getCameraState())
                       .build();
  }
}
