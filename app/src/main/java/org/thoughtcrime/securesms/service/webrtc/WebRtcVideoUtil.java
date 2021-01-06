package org.thoughtcrime.securesms.service.webrtc;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink;
import org.thoughtcrime.securesms.ringrtc.Camera;
import org.thoughtcrime.securesms.ringrtc.CameraEventListener;
import org.thoughtcrime.securesms.ringrtc.CameraState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceState;
import org.thoughtcrime.securesms.service.webrtc.state.WebRtcServiceStateBuilder;
import org.thoughtcrime.securesms.util.Util;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.VideoFrame;

/**
 * Helper for initializing, reinitializing, and deinitializing the camera and it's related
 * infrastructure.
 */
public final class WebRtcVideoUtil {

  private WebRtcVideoUtil() {}

  public static @NonNull WebRtcServiceState initializeVideo(@NonNull Context context,
                                                            @NonNull CameraEventListener cameraEventListener,
                                                            @NonNull WebRtcServiceState currentState)
  {
    final WebRtcServiceStateBuilder builder = currentState.builder();

    Util.runOnMainSync(() -> {
      EglBase            eglBase   = EglBase.create();
      BroadcastVideoSink localSink = new BroadcastVideoSink(eglBase);
      Camera             camera    = new Camera(context, cameraEventListener, eglBase, CameraState.Direction.FRONT);

      builder.changeVideoState()
             .eglBase(eglBase)
             .localSink(localSink)
             .camera(camera)
             .commit()
             .changeLocalDeviceState()
             .cameraState(camera.getCameraState())
             .commit();
    });

    return builder.build();
  }

  public static @NonNull WebRtcServiceState reinitializeCamera(@NonNull Context context,
                                                               @NonNull CameraEventListener cameraEventListener,
                                                               @NonNull WebRtcServiceState currentState)
  {
    final WebRtcServiceStateBuilder builder = currentState.builder();

    Util.runOnMainSync(() -> {
      Camera camera = currentState.getVideoState().requireCamera();
      camera.setEnabled(false);
      camera.dispose();

      camera = new Camera(context,
                          cameraEventListener,
                          currentState.getVideoState().requireEglBase(),
                          currentState.getLocalDeviceState().getCameraState().getActiveDirection());

      builder.changeVideoState()
             .camera(camera)
             .commit()
             .changeLocalDeviceState()
             .cameraState(camera.getCameraState())
             .commit();
    });

    return builder.build();
  }

  public static @NonNull WebRtcServiceState deinitializeVideo(@NonNull WebRtcServiceState currentState) {
    Camera camera = currentState.getVideoState().getCamera();
    if (camera != null) {
      camera.dispose();
    }

    EglBase eglBase = currentState.getVideoState().getEglBase();
    if (eglBase != null) {
      eglBase.release();
    }

    return currentState.builder()
                       .changeVideoState()
                       .eglBase(null)
                       .camera(null)
                       .localSink(null)
                       .commit()
                       .changeLocalDeviceState()
                       .cameraState(CameraState.UNKNOWN)
                       .build();
  }

  public static @NonNull WebRtcServiceState initializeVanityCamera(@NonNull WebRtcServiceState currentState) {
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
