package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.support.v4.view.ViewCompat;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.thoughtcrime.securesms.util.BitmapUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class QuickCamera extends SurfaceView implements SurfaceHolder.Callback {
  private SurfaceHolder surfaceHolder;
  private Camera camera;
  private QuickCameraListener listener;
  private Camera.Parameters cameraParameters;
  private boolean started, savingImage;
  private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
  private int rotation;

  public QuickCamera(Context context) {
    super(context);
    started = false;
    savingImage = false;
    try {
      initializeCamera();
      ViewGroup.LayoutParams layoutParams;
      if (cameraParameters != null) {
        if (rotation == 90 || rotation == 270)
          layoutParams = new FrameLayout.LayoutParams(cameraParameters.getPreviewSize().height, cameraParameters.getPreviewSize().width);
        else
          layoutParams = new FrameLayout.LayoutParams(cameraParameters.getPreviewSize().width, cameraParameters.getPreviewSize().height);
      } else {
        layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
      }
      setLayoutParams(layoutParams);
      surfaceHolder = getHolder();
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
      stopPreviewAndReleaseCamera();
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  private Camera getCameraInstance(int cameraId) throws RuntimeException {
    return Camera.open(cameraId);
  }

  private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) height / width;

    if (sizes == null)
      return null;

    Camera.Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;

    for (Camera.Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (Math.abs(size.height - height) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - height);
      }
    }

    if (optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for (Camera.Size size : sizes) {
        if (Math.abs(size.height - height) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - height);
        }
      }
    }
    return optimalSize;
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    startPreview();
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {}

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    stopPreviewAndReleaseCamera();
  }

  public void stopPreviewAndReleaseCamera() {
    ViewCompat.setAlpha(QuickCamera.this, 0.f);
    if (camera != null) {
      camera.stopPreview();
      camera.release();
      camera = null;
      started = false;
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {

  }

  public void takePicture(final boolean crop, final Rect previewRect) {
    if (camera != null) {
      camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
          new AsyncTask<byte[], Void, byte[]>() {
            @Override
            protected byte[] doInBackground(byte[]... params) {
              byte[] data = params[0];
              if (savingImage)
                return null;
              savingImage = true;
              try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                int previewWidth = cameraParameters.getPreviewSize().width;
                int previewHeight = cameraParameters.getPreviewSize().height;
                YuvImage previewImage = new YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null);

                if (crop) {
                  float newWidth, newHeight;
                  if (rotation == 90 || rotation == 270) {
                    newWidth = previewRect.height();
                    newHeight = previewRect.width();
                  } else {
                    newWidth = previewRect.width();
                    newHeight = previewRect.height();
                  }
                  float centerX = previewWidth / 2;
                  float centerY = previewHeight / 2;
                  previewRect.set((int) (centerX - newWidth / 2),
                      (int) (centerY - newHeight / 2),
                      (int) (centerX + newWidth / 2),
                      (int) (centerY + newHeight / 2));
                } else if (rotation == 90 || rotation == 270) {
                  previewRect.set(0, 0, previewRect.height(), previewRect.width());
                }
                previewImage.compressToJpeg(previewRect, 100, byteArrayOutputStream);
                byte[] bytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
                byteArrayOutputStream = new ByteArrayOutputStream();
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (rotation != 0)
                  bitmap = BitmapUtil.rotateBitmap(bitmap, rotation);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] finalImageByteArray = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
                savingImage = false;
                return finalImageByteArray;
              } catch (IOException e) {
                savingImage = false;
                return null;
              }
            }

            @Override
            protected void onPostExecute(byte[] data) {
              if (data != null && listener != null)
                listener.onImageCapture(data);
            }
          }.execute(data);
        }
      });
    }
  }

  private void initializeCamera() {
    if (camera == null)
      camera = getCameraInstance(cameraId);
    cameraParameters = camera.getParameters();
    List<String> focusModes = cameraParameters.getSupportedFocusModes();
    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
      cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
      cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    else
      camera.autoFocus(null);
    setCameraRotation();
  }

  private void setCameraRotation() {
    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
    int width = displayMetrics.widthPixels;
    int height = displayMetrics.heightPixels;
    Camera.CameraInfo info = new Camera.CameraInfo();
    android.hardware.Camera.getCameraInfo(cameraId, info);
    int windowRotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
    int degrees = 0;
    switch (windowRotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }

    int derivedOrientation;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      derivedOrientation = (info.orientation + degrees) % 360;
      derivedOrientation = (360 - derivedOrientation) % 360;
    } else {
      derivedOrientation = (info.orientation - degrees + 360) % 360;
    }
    camera.setDisplayOrientation(derivedOrientation);

    int orientation = (degrees + 45) / 90 * 90;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && (degrees == 0 || degrees == 180))
      rotation = (info.orientation - orientation + 360) % 360;
    else
      rotation = derivedOrientation;
    cameraParameters.setRotation(rotation);

    Camera.Size previewSize;
    if (rotation == 0 || rotation == 180)
      previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), width, height);
    else
      previewSize = getOptimalPreviewSize(cameraParameters.getSupportedPreviewSizes(), height, width);
    if (previewSize != null)
      cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
  }

  public boolean isStarted() {
    return started;
  }

  public boolean startPreview() {
    if (started)
      stopPreviewAndReleaseCamera();
    try {
      initializeCamera();
      camera.setParameters(cameraParameters);
      camera.setPreviewDisplay(surfaceHolder);
      ViewCompat.setAlpha(this, 0.f);
      camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
          ViewCompat.setAlpha(QuickCamera.this, 1.f);
        }
      });
      camera.startPreview();
      started = true;
    } catch (RuntimeException | IOException e) {
      if (listener != null) listener.displayCameraUnavailableError();
      return false;
    }
    return true;
  }

  public void setQuickCameraListener(QuickCameraListener listener) {
    this.listener = listener;
  }

  public boolean isMultipleCameras() {
    return Camera.getNumberOfCameras() > 1;
  }

  public void swapCamera() {
    if (isMultipleCameras()) {
      if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
        cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
      else
        cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
      stopPreviewAndReleaseCamera();
      startPreview();
    }
  }

  public boolean isRearCamera() {
    return cameraId == Camera.CameraInfo.CAMERA_FACING_BACK;
  }

  public interface QuickCameraListener {
    void displayCameraUnavailableError();
    void onImageCapture(final byte[] data);
  }
}