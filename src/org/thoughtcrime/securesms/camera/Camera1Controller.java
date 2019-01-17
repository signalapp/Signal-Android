package org.thoughtcrime.securesms.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.view.Surface;

import org.thoughtcrime.securesms.logging.Log;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera1Controller {

  private static final String TAG = Camera1Controller.class.getSimpleName();

  private Camera               camera;
  private int                  cameraId;
  private OrderEnforcer<Stage> enforcer;
  private EventListener        eventListener;
  private SurfaceTexture       previewSurface;
  private int                  screenRotation;

  public Camera1Controller(int preferredDirection, @NonNull EventListener eventListener) {
    this.eventListener = eventListener;
    this.enforcer      = new OrderEnforcer<>(Stage.INITIALIZED, Stage.PREVIEW_STARTED);
    this.cameraId      = Camera.getNumberOfCameras() > 1  ? preferredDirection : Camera.CameraInfo.CAMERA_FACING_BACK;
  }

  public void initialize() {
    Log.d(TAG, "initialize()");

    if (Camera.getNumberOfCameras() <= 0) {
      onCameraUnavailable();
    }

    camera = Camera.open(cameraId);

    Camera.Parameters  params     = camera.getParameters();
    Camera.Size        maxSize    = getMaxSupportedPreviewSize(camera);
    final List<String> focusModes = params.getSupportedFocusModes();

    params.setPreviewSize(maxSize.width, maxSize.height);

    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
      params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
      params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    camera.setParameters(params);

    enforcer.markCompleted(Stage.INITIALIZED);

    eventListener.onPropertiesAvailable(getProperties());
  }

  public void release() {
    Log.d(TAG, "release() called");
    enforcer.run(Stage.PREVIEW_STARTED, () -> {
      Log.d(TAG, "release() executing");
      previewSurface = null;
      camera.stopPreview();
      camera.release();
      enforcer.reset();
    });
  }

  public void linkSurface(@NonNull SurfaceTexture surfaceTexture) {
    Log.d(TAG, "linkSurface() called");
    enforcer.run(Stage.INITIALIZED, () -> {
      try {
        Log.d(TAG, "linkSurface() executing");
        previewSurface = surfaceTexture;

        camera.setPreviewTexture(surfaceTexture);
        camera.startPreview();
        enforcer.markCompleted(Stage.PREVIEW_STARTED);
      } catch (Exception e) {
        Log.w(TAG, "Failed to start preview.", e);
        eventListener.onCameraUnavailable();
      }
    });
  }

  public int flip() {
    Log.d(TAG, "flip()");
    SurfaceTexture surfaceTexture = previewSurface;
    cameraId = (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;

    release();
    initialize();
    linkSurface(surfaceTexture);
    setScreenRotation(screenRotation);

    return cameraId;
  }

  public void setScreenRotation(int screenRotation) {
    Log.d(TAG, "setScreenRotation(" + screenRotation + ") called");
    enforcer.run(Stage.PREVIEW_STARTED, () -> {
      Log.d(TAG, "setScreenRotation(" + screenRotation + ") executing");
      this.screenRotation = screenRotation;

      int rotation = getCameraRotationForScreen(screenRotation);
      camera.setDisplayOrientation(rotation);

      Log.d(TAG, "Set camera rotation to: " + rotation);

      Camera.Parameters params = camera.getParameters();
      params.setRotation(rotation);
      camera.setParameters(params);
    });
  }

  private void onCameraUnavailable() {
    enforcer.reset();
    eventListener.onCameraUnavailable();
  }

  private Properties getProperties() {
    Camera.Size previewSize = camera.getParameters().getPreviewSize();
    return new Properties(Camera.getNumberOfCameras(), previewSize.width, previewSize.height);
  }

  private Camera.Size getMaxSupportedPreviewSize(Camera camera) {
    List<Camera.Size> cameraSizes = camera.getParameters().getSupportedPreviewSizes();
    Collections.sort(cameraSizes, DESC_SIZE_COMPARATOR);
    return cameraSizes.get(0);
  }

  private int getCameraRotationForScreen(int screenRotation) {
    int degrees = 0;

    switch (screenRotation) {
      case Surface.ROTATION_0:   degrees = 0;   break;
      case Surface.ROTATION_90:  degrees = 90;  break;
      case Surface.ROTATION_180: degrees = 180; break;
      case Surface.ROTATION_270: degrees = 270; break;
    }

    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return (360 - ((info.orientation + degrees) % 360)) % 360;
    } else {
      return (info.orientation - degrees + 360) % 360;
    }
  }

  private final Comparator<Camera.Size> DESC_SIZE_COMPARATOR = (o1, o2) -> Integer.compare(o2.width * o2.height, o1.width * o1.height);

  private enum Stage {
    INITIALIZED, PREVIEW_STARTED
  }

  class Properties {

    private final int cameraCount;
    private final int previewWidth;
    private final int previewHeight;

    public Properties(int cameraCount, int previewWidth, int previewHeight) {
      this.cameraCount   = cameraCount;
      this.previewWidth  = previewWidth;
      this.previewHeight = previewHeight;
    }

    int getCameraCount() {
      return cameraCount;
    }

    public int getPreviewWidth() {
      return previewWidth;
    }

    public int getPreviewHeight() {
      return previewHeight;
    }

    @Override
    public String toString() {
      return "cameraCount: " + camera + "  previewWidth: " + previewWidth + "  previewHeight: " + previewHeight;
    }
  }

  interface EventListener {
    void onPropertiesAvailable(@NonNull Properties properties);
    void onCameraUnavailable();
  }
}
