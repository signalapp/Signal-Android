/***
 Copyright (c) 2013-2014 CommonsWare, LLC
 Portions Copyright (C) 2007 The Android Open Source Project

 Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except in compliance with the License. You may obtain
 a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.thoughtcrime.securesms.components.camera;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.AsyncTask;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import java.io.IOException;

import com.commonsware.cwac.camera.CameraHost;
import com.commonsware.cwac.camera.CameraHost.FailureReason;
import com.commonsware.cwac.camera.PreviewStrategy;

import org.thoughtcrime.securesms.util.Util;

@SuppressWarnings("deprecation")
public class CameraView extends ViewGroup {
  private static final String TAG = CameraView.class.getSimpleName();

  private PreviewStrategy     previewStrategy;
  private Camera.Size         previewSize;
  private Camera              camera                 = null;
  private boolean             inPreview              = false;
  private CameraHost          host                   = null;
  private OnOrientationChange onOrientationChange    = null;
  private int                 displayOrientation     = -1;
  private int                 outputOrientation      = -1;
  private int                 cameraId               = -1;
  private int                 lastPictureOrientation = -1;

  public CameraView(Context context) {
    this(context, null);
  }

  public CameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);

    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
  }

  public CameraHost getHost() {
    return host;
  }

  public void setHost(CameraHost host) {
    this.host = host;

    if (host.getDeviceProfile().useTextureView()) {
      previewStrategy = new TexturePreviewStrategy(this);
    } else {
      previewStrategy = new SurfacePreviewStrategy(this);
    }
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void onResume() {
    final CameraHost host = getHost();
    new AsyncTask<Void, Void, Pair<Camera,FailureReason>>() {
      @Override protected Pair<Camera,FailureReason> doInBackground(Void... params) {
        final Camera camera;
        try {
          cameraId = host.getCameraId();

          if (cameraId >= 0) {
            camera = Camera.open(cameraId);
          } else {
            return new Pair<>(null, FailureReason.NO_CAMERAS_REPORTED);
          }
        } catch (Exception e) {
          return new Pair<>(null, FailureReason.UNKNOWN);
        }
        return new Pair<>(camera, null);
      }

      @Override protected void onPostExecute(Pair<Camera, FailureReason> result) {
        if (result.second != null) {
          host.onCameraFail(result.second);
          return;
        }
        if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
          onOrientationChange.enable();
        }
        camera = result.first;
        setCameraDisplayOrientation();
        requestLayout();
        invalidate();
        synchronized (CameraView.this) {
          CameraView.this.notifyAll();
        }
      }
    }.execute();
    addView(previewStrategy.getWidget());
  }

  public void onPause() {
    if (camera == null || previewSize == null) {
      synchronized (CameraView.this) {
        while (camera == null || previewSize == null) {
          Util.wait(CameraView.this, 0);
        }
      }
    }

    if (camera != null) {
      previewDestroyed();
    }
    removeView(previewStrategy.getWidget());
    onOrientationChange.disable();
    previewSize = null;
    displayOrientation     = -1;
    outputOrientation      = -1;
    cameraId               = -1;
    lastPictureOrientation = -1;
  }

  // based on CameraPreview.java from ApiDemos

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int width  = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);

    if (width > 0 && height > 0 && camera != null) {
      Camera.Size newSize = null;

      try {
        if (getHost().getRecordingHint() != CameraHost.RecordingHint.STILL_ONLY) {
          newSize = getHost().getPreferredPreviewSizeForVideo(getDisplayOrientation(),
                                                              width,
                                                              height,
                                                              camera.getParameters(),
                                                              null);
        }
        if (newSize == null || newSize.width * newSize.height < 65536) {
          newSize = getHost().getPreviewSize(getDisplayOrientation(),
                                             width, height,
                                             camera.getParameters());
        }
      } catch (Exception e) {
        Log.e(TAG, "Could not work with camera parameters?", e);
        // TODO get this out to library clients
      }

      if (newSize != null) {
        if (previewSize == null) {
          previewSize = newSize;
          synchronized (this) { notifyAll(); }
        } else if (previewSize.width != newSize.width || previewSize.height != newSize.height) {
          if (inPreview) {
            stopPreview();
          }

          previewSize = newSize;
          synchronized (this) { notifyAll(); }
          initPreview(width, height, false);
        }
      }
    }
  }

  // based on CameraPreview.java from ApiDemos

  @SuppressWarnings("SuspiciousNameCombination") @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (changed && getChildCount() > 0) {
      final View child         = getChildAt(0);
      final int  width         = r - l;
      final int  height        = b - t;
      final int  previewWidth;
      final int  previewHeight;

      // handle orientation

      if (previewSize != null && (getDisplayOrientation() == 90 || getDisplayOrientation() == 270)) {
        previewWidth  = previewSize.height;
        previewHeight = previewSize.width;
      } else if (previewSize != null) {
        previewWidth  = previewSize.width;
        previewHeight = previewSize.height;
      } else {
        previewWidth  = width;
        previewHeight = height;
      }

      boolean useFirstStrategy = (width * previewHeight > height * previewWidth);
      boolean useFullBleed     = getHost().useFullBleedPreview();

      if ((useFirstStrategy && !useFullBleed) || (!useFirstStrategy && useFullBleed)) {
        final int scaledChildWidth = previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
      } else {
        final int scaledChildHeight = previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
      }
    }
  }

  public int getDisplayOrientation() {
    return displayOrientation;
  }

  public void setOneShotPreviewCallback(PreviewCallback callback) {
    if (camera != null) camera.setOneShotPreviewCallback(callback);
  }

  public Camera.Parameters getCameraParameters() {
    return camera.getParameters();
  }

  void previewCreated() {
    final CameraHost host = getHost();
    new PostInitializationTask() {
      @Override
      public void onPostExecute(Void aVoid) {
        try {
          if (camera != null) {
            previewStrategy.attach(camera);
          }
        } catch (IOException e) {
          host.handleException(e);
        }
      }
    }.execute();
  }

  void previewDestroyed() {
    if (camera != null) {
      previewStopped();
      camera.release();
      camera = null;
    }
  }

  void previewReset(int width, int height) {
    previewStopped();
    initPreview(width, height);
  }

  private void previewStopped() {
    if (inPreview) {
      stopPreview();
    }
  }

  public void initPreview(int w, int h) {
    initPreview(w, h, true);
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void initPreview(int w, int h, boolean firstRun) {
    new PostInitializationTask() {
      @Override protected void onPostExecute(Void aVoid) {
        if (camera != null) {
          Camera.Parameters parameters = camera.getParameters();

          parameters.setPreviewSize(previewSize.width, previewSize.height);

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setRecordingHint(getHost().getRecordingHint() != CameraHost.RecordingHint.STILL_ONLY);
          }

          camera.setParameters(getHost().adjustPreviewParameters(parameters));
          startPreview();
          requestLayout();
          invalidate();
        }
      }
    }.execute();
  }

  private void startPreview() {
    camera.startPreview();
    inPreview = true;
    getHost().autoFocusAvailable();
  }

  private void stopPreview() {
    inPreview = false;
    getHost().autoFocusUnavailable();
    camera.stopPreview();
  }

  // based on
  // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
  // and http://stackoverflow.com/a/10383164/115145
  private void setCameraDisplayOrientation() {
    Camera.CameraInfo info     = new Camera.CameraInfo();
    int               rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
    int               degrees  = 0;
    DisplayMetrics    dm       = new DisplayMetrics();

    Camera.getCameraInfo(cameraId, info);
    getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

    switch (rotation) {
    case Surface.ROTATION_0:   degrees = 0;   break;
    case Surface.ROTATION_90:  degrees = 90;  break;
    case Surface.ROTATION_180: degrees = 180; break;
    case Surface.ROTATION_270: degrees = 270; break;
    }

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      displayOrientation = (info.orientation + degrees           ) % 360;
      displayOrientation = (360              - displayOrientation) % 360;
    }
    else {
      displayOrientation = (info.orientation - degrees + 360) % 360;
    }

    boolean wasInPreview = inPreview;

    if (inPreview) {
      stopPreview();
    }

    camera.setDisplayOrientation(displayOrientation);

    if (wasInPreview) {
      startPreview();
    }
  }

  public int getCameraPictureOrientation() {
    Camera.CameraInfo info = new Camera.CameraInfo();

    Camera.getCameraInfo(cameraId, info);

    if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      outputOrientation = getCameraPictureRotation(getActivity().getWindowManager()
                                                                .getDefaultDisplay()
                                                                .getOrientation());
    } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      outputOrientation = (360 - displayOrientation) % 360;
    } else {
      outputOrientation = displayOrientation;
    }

    if (lastPictureOrientation != outputOrientation) {
      lastPictureOrientation = outputOrientation;
    }
    return outputOrientation;
  }

  // based on:
  // http://developer.android.com/reference/android/hardware/Camera.Parameters.html#setRotation(int)

  public int getCameraPictureRotation(int orientation) {
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    int rotation;

    orientation = (orientation + 45) / 90 * 90;

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      rotation = (info.orientation - orientation + 360) % 360;
    }
    else { // back-facing camera
      rotation = (info.orientation + orientation) % 360;
    }

    return rotation;
  }

  Activity getActivity() {
    return (Activity)getContext();
  }

  private class OnOrientationChange extends OrientationEventListener {
    public OnOrientationChange(Context context) {
      super(context);
      disable();
    }

    @Override
    public void onOrientationChanged(int orientation) {
      if (camera != null && orientation != ORIENTATION_UNKNOWN) {
        int newOutputOrientation = getCameraPictureRotation(orientation);

        if (newOutputOrientation != outputOrientation) {
          outputOrientation = newOutputOrientation;

          Camera.Parameters params = camera.getParameters();

          params.setRotation(outputOrientation);

          try {
            camera.setParameters(params);
            lastPictureOrientation = outputOrientation;
          }
          catch (Exception e) {
            Log.e(TAG, "Exception updating camera parameters in orientation change", e);
          }
        }
      }
    }
  }

  private class PostInitializationTask extends AsyncTask<Void,Void,Void> {

    @Override protected Void doInBackground(Void... params) {
      synchronized (CameraView.this) {
        while (camera == null || previewSize == null) {
          Util.wait(CameraView.this, 0);
        }
      }
      return null;
    }
  }
}