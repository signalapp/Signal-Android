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
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Build.VERSION;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.FrameLayout;

import java.io.IOException;

import com.commonsware.cwac.camera.CameraHost;
import com.commonsware.cwac.camera.CameraHost.FailureReason;
import com.commonsware.cwac.camera.CameraUtils;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.util.guava.Optional;

@SuppressWarnings("deprecation")
public class CameraView extends FrameLayout {
  private static final String TAG = CameraView.class.getSimpleName();

  private final OnOrientationChange onOrientationChange;

  private @NonNull volatile Optional<Camera> camera = Optional.absent();

  private CameraHost        host;
  private CameraSurfaceView surface;

  private int displayOrientation     = -1;
  private int outputOrientation      = -1;
  private int lastPictureOrientation = -1;

  public CameraView(Context context) {
    this(context, null);
  }

  public CameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setBackgroundColor(Color.BLACK);

    surface             = new CameraSurfaceView(getContext());
    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
    addView(surface);
  }

  public void setHost(@NonNull CameraHost host) {
    this.host = host;
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void onResume() {
    Log.w(TAG, "onResume() queued");
    enqueueTask(new SerialAsyncTask<Pair<Camera, FailureReason>>() {
      @Override protected Pair<Camera, FailureReason> onRunBackground() {
        try {
          if (host.getCameraId() >= 0) {
            return new Pair<>(Camera.open(host.getCameraId()), null);
          } else {
            return new Pair<>(null, FailureReason.NO_CAMERAS_REPORTED);
          }
        } catch (Exception e) {
          return new Pair<>(null, FailureReason.UNKNOWN);
        }
      }

      @Override protected void onPostMain(Pair<Camera, FailureReason> result) {
        if (result.first == null) {
          host.onCameraFail(result.second);
          return;
        }

        camera = Optional.of(result.first);
        try {
          if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            onOrientationChange.enable();
          }
          setCameraDisplayOrientation();
          synchronized (CameraView.this) {
            CameraView.this.notifyAll();
          }
          onCameraReady();
          requestLayout();
          invalidate();
          Log.w(TAG, "onResume() completed");
        } catch (RuntimeException e) {
          Log.w(TAG, "exception when starting camera preview", e);
          onPause();
        }
      }
    });
  }

  public void onPause() {
    Log.w(TAG, "onPause() queued");
    final Optional<Camera> cameraToDestroy = camera;

    enqueueTask(new SerialAsyncTask<Void>() {
      @Override protected void onPreMain() {
        camera = Optional.absent();
      }

      @Override protected Void onRunBackground() {
        if (cameraToDestroy.isPresent()) {
          stopPreview();
          cameraToDestroy.get().release();
        }
        return null;
      }

      @Override protected void onPostMain(Void avoid) {
        onOrientationChange.disable();
        displayOrientation = -1;
        outputOrientation = -1;
        lastPictureOrientation = -1;
        Log.w(TAG, "onPause() completed");
      }
    });
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0 && camera.isPresent()) {
      final Size       preferredPreviewSize = getPreferredPreviewSize(camera.get());
      final Parameters parameters           = camera.get().getParameters();

      if (preferredPreviewSize != null && !parameters.getPreviewSize().equals(preferredPreviewSize)) {
        stopPreview();
        parameters.setPreviewSize(preferredPreviewSize.width, preferredPreviewSize.height);
        camera.get().setParameters(parameters);
        requestLayout();
        startPreview();
      }
    }
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int  width         = r - l;
    final int  height        = b - t;
    final int  previewWidth;
    final int  previewHeight;

    if (camera.isPresent()) {
      final Size previewSize = camera.get().getParameters().getPreviewSize();
      if (getDisplayOrientation() == 90 || getDisplayOrientation() == 270) {
        previewWidth  = previewSize.height;
        previewHeight = previewSize.width;
      } else {
        previewWidth  = previewSize.width;
        previewHeight = previewSize.height;
      }
    } else {
      previewWidth  = width;
      previewHeight = height;
    }

    Log.w(TAG, "width " + width + "x" + height);
    Log.w(TAG, "layout preview target " + previewWidth + "x" + previewHeight);

    if (previewHeight == 0 || previewWidth == 0) {
      Log.w(TAG, "skipping layout due to zero-width/height preview size");
      return;
    }

    boolean useFirstStrategy = (width * previewHeight > height * previewWidth);

    if (!useFirstStrategy) {
      final int scaledChildWidth = previewWidth * height / previewHeight;
      surface.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
    } else {
      final int scaledChildHeight = previewHeight * width / previewWidth;
      surface.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
    }
  }

  public int getDisplayOrientation() {
    return displayOrientation;
  }

  public void setOneShotPreviewCallback(PreviewCallback callback) {
    if (camera.isPresent()) camera.get().setOneShotPreviewCallback(callback);
  }

  private void onCameraReady() {
    if (!camera.isPresent()) return;

    final Parameters parameters = camera.get().getParameters();
    if (VERSION.SDK_INT >= 14) parameters.setRecordingHint(true);
//    parameters.setPictureFormat(ImageFormat.NV21);
    camera.get().setParameters(parameters);

    enqueueTask(new PostInitializationTask<Void>() {
      @Override protected void onPostMain(Void avoid) {
        if (camera.isPresent()) {
          try {
            camera.get().setPreviewDisplay(surface.getHolder());
            startPreview();
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }
      }
    });
  }

  @TargetApi(11)
  private @Nullable Size getPreferredPreviewSize(@NonNull Camera camera) {
    final Camera.Parameters parameters = camera.getParameters();
    Log.w(TAG, String.format("original preview size: %dx%d", parameters.getPreviewSize().width, parameters.getPreviewSize().height));
    Size preferredSize = VERSION.SDK_INT > 11 ? camera.getParameters().getPreferredPreviewSizeForVideo() : null;
    if (preferredSize == null) {
      preferredSize = CameraUtils.getBestAspectPreviewSize(getDisplayOrientation(),
                                                           getMeasuredWidth(),
                                                           getMeasuredHeight(),
                                                           parameters);
    }

    return preferredSize;
  }

  private void startPreview() {
    if (camera.isPresent()) {
      camera.get().startPreview();
      host.autoFocusAvailable();
    }
  }

  private void stopPreview() {
    if (camera.isPresent()) {
      host.autoFocusUnavailable();
      camera.get().stopPreview();
    }
  }

  // based on
  // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
  // and http://stackoverflow.com/a/10383164/115145
  private void setCameraDisplayOrientation() {
    Camera.CameraInfo info     = getCameraInfo();
    int               rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
    int               degrees  = 0;
    DisplayMetrics    dm       = new DisplayMetrics();

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

    stopPreview();
    camera.get().setDisplayOrientation(displayOrientation);
    startPreview();
  }

  public int getCameraPictureOrientation() {
    if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      outputOrientation = getCameraPictureRotation(getActivity().getWindowManager()
                                                                .getDefaultDisplay()
                                                                .getOrientation());
    } else if (getCameraInfo().facing == CameraInfo.CAMERA_FACING_FRONT) {
      outputOrientation = (360 - displayOrientation) % 360;
    } else {
      outputOrientation = displayOrientation;
    }

    if (lastPictureOrientation != outputOrientation) {
      lastPictureOrientation = outputOrientation;
    }
    return outputOrientation;
  }

  private @NonNull CameraInfo getCameraInfo() {
    final CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(host.getCameraId(), info);
    return info;
  }

  // XXX this sucks
  private Activity getActivity() {
    return (Activity)getContext();
  }

  protected @NonNull Optional<Camera> getCamera() {
    return camera;
  }

  public int getCameraPictureRotation(int orientation) {
    final CameraInfo info = getCameraInfo();
    final int        rotation;

    orientation = (orientation + 45) / 90 * 90;

    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      rotation = (info.orientation - orientation + 360) % 360;
    } else {
      rotation = (info.orientation + orientation) % 360;
    }

    return rotation;
  }

  private class OnOrientationChange extends OrientationEventListener {
    public OnOrientationChange(Context context) {
      super(context);
      disable();
    }

    @Override
    public void onOrientationChanged(int orientation) {
      if (camera.isPresent() && orientation != ORIENTATION_UNKNOWN) {
        int newOutputOrientation = getCameraPictureRotation(orientation);

        if (newOutputOrientation != outputOrientation) {
          outputOrientation = newOutputOrientation;

          Camera.Parameters params = camera.get().getParameters();

          params.setRotation(outputOrientation);

          try {
            camera.get().setParameters(params);
            lastPictureOrientation = outputOrientation;
          }
          catch (Exception e) {
            Log.e(TAG, "Exception updating camera parameters in orientation change", e);
          }
        }
      }
    }
  }

  private void enqueueTask(SerialAsyncTask job) {
    ApplicationContext.getInstance(getContext()).getJobManager().add(job);
  }

  private static abstract class SerialAsyncTask<Result> extends Job {

    public SerialAsyncTask() {
      super(JobParameters.newBuilder().withGroupId(CameraView.class.getSimpleName()).create());
    }

    @Override public void onAdded() {}

    @Override public final void onRun() {
      try {
        onWait();
        Util.runOnMainSync(new Runnable() {
          @Override public void run() {
            onPreMain();
          }
        });

        final Result result = onRunBackground();

        Util.runOnMainSync(new Runnable() {
          @Override public void run() {
            onPostMain(result);
          }
        });
      } catch (PreconditionsNotMetException e) {
        Log.w(TAG, "skipping task, preconditions not met in onWait()");
      }
    }

    @Override public boolean onShouldRetry(Exception e) {
      return false;
    }

    @Override public void onCanceled() { }

    protected void onWait() throws PreconditionsNotMetException {}
    protected void onPreMain() {}
    protected Result onRunBackground() { return null; }
    protected void onPostMain(Result result) {}
  }

  private abstract class PostInitializationTask<Result> extends SerialAsyncTask<Result> {
    @Override protected void onWait() throws PreconditionsNotMetException {
      synchronized (CameraView.this) {
        if (!camera.isPresent()) {
          Log.w(TAG, "throwing preconditions not met");
          throw new PreconditionsNotMetException();
        }
        while (getMeasuredHeight() <= 0 || getMeasuredWidth() <= 0 || !surface.isReady()) {
          Log.w(TAG, String.format("waiting. prevewStrategy? %s", surface.isReady()));
          Util.wait(CameraView.this, 0);
        }
        Log.w(TAG, "done waiting!");
      }
    }
  }

  private static class PreconditionsNotMetException extends Exception {}
}
