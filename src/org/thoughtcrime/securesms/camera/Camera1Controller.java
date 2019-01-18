package org.thoughtcrime.securesms.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.annotation.NonNull;
import android.view.Surface;

import org.thoughtcrime.securesms.logging.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Camera1Controller {

  private static final String TAG = Camera1Controller.class.getSimpleName();

  private final int screenWidth;
  private final int screenHeight;

  private Camera               camera;
  private int                  cameraId;
  private OrderEnforcer<Stage> enforcer;
  private EventListener        eventListener;
  private SurfaceTexture       previewSurface;
  private int                  screenRotation;

  public Camera1Controller(int preferredDirection, int screenWidth, int screenHeight, @NonNull EventListener eventListener) {
    this.eventListener = eventListener;
    this.enforcer      = new OrderEnforcer<>(Stage.INITIALIZED, Stage.PREVIEW_STARTED);
    this.cameraId      = Camera.getNumberOfCameras() > 1  ? preferredDirection : Camera.CameraInfo.CAMERA_FACING_BACK;
    this.screenWidth   = screenWidth;
    this.screenHeight  = screenHeight;
  }

  public void initialize() {
    Log.d(TAG, "initialize()");

    if (Camera.getNumberOfCameras() <= 0) {
      onCameraUnavailable();
      return;
    }

    try {
      camera = Camera.open(cameraId);
    } catch (Exception e) {
      onCameraUnavailable();
      return;
    }

    if (camera == null) {
      onCameraUnavailable();
      return;
    }

    Camera.Parameters  params      = camera.getParameters();
    Camera.Size        previewSize = getClosestSize(camera.getParameters().getSupportedPreviewSizes(), screenWidth, screenHeight);
    Camera.Size        pictureSize = getClosestSize(camera.getParameters().getSupportedPictureSizes(), screenWidth, screenHeight);
    final List<String> focusModes  = params.getSupportedFocusModes();

    Log.d(TAG, "Preview size: " + previewSize.width + "x" + previewSize.height + "  Picture size: " + pictureSize.width + "x" + pictureSize.height);

    params.setPreviewSize(previewSize.width, previewSize.height);
    params.setPictureSize(pictureSize.width, pictureSize.height);
    params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
    params.setColorEffect(Camera.Parameters.EFFECT_NONE);
    params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);

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

  public void capture(@NonNull CaptureCallback callback) {
    enforcer.run(Stage.PREVIEW_STARTED, () -> {
      camera.takePicture(null, null, null, (data, camera) -> {
        callback.onCaptureAvailable(data, cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT);
      });
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

      int previewRotation = getPreviewRotation(screenRotation);
      int outputRotation  = getOutputRotation(screenRotation);

      Log.d(TAG, "Preview rotation: " + previewRotation + "  Output rotation: " + outputRotation);

      camera.setDisplayOrientation(previewRotation);

      Camera.Parameters params = camera.getParameters();
      params.setRotation(outputRotation);
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

  private Camera.Size getClosestSize(List<Camera.Size> sizes, int width, int height) {
    Collections.sort(sizes, ASC_SIZE_COMPARATOR);

    int i = 0;
    while (i < sizes.size() && (sizes.get(i).width * sizes.get(i).height) < (width * height)) {
      i++;
    }

    return sizes.get(Math.min(i, sizes.size() - 1));
  }

  private int getOutputRotation(int displayRotationCode) {
    int degrees = convertRotationToDegrees(displayRotationCode);

    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return (info.orientation + degrees) % 360;
    } else {
      return (info.orientation - degrees + 360) % 360;
    }
  }

  private int getPreviewRotation(int displayRotationCode) {
    int degrees = convertRotationToDegrees(displayRotationCode);

    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);

    int result;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360;
    } else {
      result = (info.orientation - degrees + 360) % 360;
    }

    return result;
  }

  private int convertRotationToDegrees(int screenRotation) {
    switch (screenRotation) {
      case Surface.ROTATION_0:   return 0;
      case Surface.ROTATION_90:  return 90;
      case Surface.ROTATION_180: return 180;
      case Surface.ROTATION_270: return 270;
    }
    return 0;
  }

  private final Comparator<Camera.Size> ASC_SIZE_COMPARATOR = (o1, o2) -> Integer.compare(o1.width * o1.height, o2.width * o2.height);

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
      return "cameraCount: " + cameraCount + "  previewWidth: " + previewWidth + "  previewHeight: " + previewHeight;
    }
  }

  interface EventListener {
    void onPropertiesAvailable(@NonNull Properties properties);
    void onCameraUnavailable();
  }

  interface CaptureCallback {
    void onCaptureAvailable(@NonNull byte[] jpegData, boolean frontFacing);
  }
}
