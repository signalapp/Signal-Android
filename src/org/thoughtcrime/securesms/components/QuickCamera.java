package org.thoughtcrime.securesms.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.view.ViewGroup;
import android.widget.Toast;

import com.commonsware.cwac.camera.SimpleCameraHost;

import org.thoughtcrime.securesms.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class QuickCamera extends CameraView {
  private QuickCameraListener listener;
  private boolean started, savingImage;
  private int rotation;
  private QuickCameraHost cameraHost;

  public QuickCamera(Context context) {
    super(context);
    started = false;
    savingImage = false;
    setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    cameraHost = new QuickCameraHost(context);
    setHost(cameraHost);
  }

  @Override
  public void onResume() {
    super.onResume();
    rotation = getCameraPictureOrientation();
    started = true;
  }

  @Override
  public void onPause() {
    started = false;
    super.onPause();
  }

  public boolean isStarted() {
    return started;
  }

  public void takePicture(final boolean crop, final Rect previewRect) {
    setOneShotPreviewCallback(new Camera.PreviewCallback() {
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
              int previewWidth = getCameraParameters().getPreviewSize().width;
              int previewHeight = getCameraParameters().getPreviewSize().height;
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
                bitmap = rotateBitmap(bitmap, rotation);
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

  private static Bitmap rotateBitmap(Bitmap bitmap, int angle) {
    Matrix matrix = new Matrix();
    matrix.postRotate(angle);
    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    if (rotated != bitmap) bitmap.recycle();
    return rotated;
  }

  public void setQuickCameraListener(QuickCameraListener listener) {
    this.listener = listener;
  }

  public boolean isMultipleCameras() {
    return Camera.getNumberOfCameras() > 1;
  }

  public boolean isRearCamera() {
    return cameraHost.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK;
  }

  public void swapCamera() {
    cameraHost.swapCameraId();
    onPause();
    onResume();
  }

  public interface QuickCameraListener {
    void onImageCapture(final byte[] data);
  }

  private class QuickCameraHost extends SimpleCameraHost {
    int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

    public QuickCameraHost(Context context) {
      super(context);
    }

    @Override
    public Camera.Parameters adjustPreviewParameters(Camera.Parameters parameters) {
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
      return parameters;
    }

    @Override
    public int getCameraId() {
      return cameraId;
    }

    public void swapCameraId() {
      if (isMultipleCameras()) {
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
          cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        else
          cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
      }
    }

    @Override
    public void onCameraFail(FailureReason reason) {
      super.onCameraFail(reason);
      Toast.makeText(getContext(), R.string.quick_camera_unavailable, Toast.LENGTH_SHORT).show();
    }
  }
}