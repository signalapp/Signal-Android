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
import android.hardware.Camera.PreviewCallback;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import com.commonsware.cwac.camera.CameraHost;
import com.commonsware.cwac.camera.CameraHost.FailureReason;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;

@SuppressWarnings("deprecation")
public class CameraView extends FrameLayout {
  private static final String TAG = CameraView.class.getSimpleName();

  private final OnOrientationChange onOrientationChange;

  private          PreviewStrategy     previewStrategy        = null;
  private          Camera.Size         previewSize            = null;
  private volatile Camera              camera                 = null;
  private          boolean             inPreview              = false;
  private          boolean             cameraReady;
  private          CameraHost          host;
  private          int                 displayOrientation     = -1;
  private          int                 outputOrientation      = -1;
  private          int                 lastPictureOrientation = -1;

  public CameraView(Context context) {
    this(context, null);
  }

  public CameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setBackgroundColor(Color.BLACK);

    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
  }

  public void setHost(@NonNull CameraHost host) {
    this.host = host;

    if (host.getDeviceProfile().useTextureView()) {
      previewStrategy = new TexturePreviewStrategy(this);
    } else {
      previewStrategy = new SurfacePreviewStrategy(this);
    }
    addView(previewStrategy.getWidget());
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void onResume() {
    Log.w(TAG, "onResume() queued");
    enqueueTask(new SerialAsyncTask<FailureReason>() {
      @Override protected FailureReason onRunBackground() {
        try {
          if (host.getCameraId() >= 0) {
            camera = Camera.open(host.getCameraId());
          } else {
            return FailureReason.NO_CAMERAS_REPORTED;
          }
        } catch (Exception e) {
          return FailureReason.UNKNOWN;
        }

        return null;
      }

      @Override protected void onPostMain(FailureReason result) {
        if (result != null) {
          host.onCameraFail(result);
          return;
        }
        try {
          cameraReady = true;
          if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            onOrientationChange.enable();
          }
          setCameraDisplayOrientation();
          synchronized (CameraView.this) {
            CameraView.this.notifyAll();
          }
          previewCreated();
          initPreview();
          requestLayout();
          invalidate();
          Log.w(TAG, "onResume() completed");
        } catch (RuntimeException re) {
          Log.w(TAG, "exception when starting camera preview", re);
          try {
            previewDestroyed();
          } catch (RuntimeException re2) {
            Log.w(TAG, "also failed to release camera", re2);
          }
        }
      }
    });
  }

  public void onPause() {
    Log.w(TAG, "onPause() queued");
    enqueueTask(new SerialAsyncTask<Void>() {
      @Override protected void onPreMain() {
        cameraReady = false;
      }

      @Override protected Void onRunBackground() {
        previewDestroyed();
        return null;
      }

      @Override protected void onPostMain(Void avoid) {
        onOrientationChange.disable();
        previewSize = null;
        displayOrientation = -1;
        outputOrientation = -1;
        lastPictureOrientation = -1;
        Log.w(TAG, "onPause() completed");
      }
    });
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0 && camera != null && cameraReady) {
      Camera.Size newSize = null;

      try {
        if (host.getRecordingHint() != CameraHost.RecordingHint.STILL_ONLY) {
          newSize = host.getPreferredPreviewSizeForVideo(getDisplayOrientation(),
                                                              getMeasuredWidth(),
                                                              getMeasuredHeight(),
                                                              camera.getParameters(),
                                                              null);
        }
        if (newSize == null || newSize.width * newSize.height < 65536) {
          newSize = host.getPreviewSize(getDisplayOrientation(),
                                             getMeasuredWidth(),
                                             getMeasuredHeight(),
                                             camera.getParameters());
        }
      } catch (Exception e) {
        Log.e(TAG, "Could not work with camera parameters?", e);
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
          initPreview();
        }
      }
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final View child         = previewStrategy.getWidget();
    final int  width         = r - l;
    final int  height        = b - t;

    final int  previewWidth;
    final int  previewHeight;
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

    if (previewHeight == 0 || previewWidth == 0) {
      Log.w(TAG, "skipping layout due to zero-width/height preview size");
      return;
    }

    boolean useFirstStrategy = (width * previewHeight > height * previewWidth);
    boolean useFullBleed     = host.useFullBleedPreview();

    if ((useFirstStrategy && !useFullBleed) || (!useFirstStrategy && useFullBleed)) {
      final int scaledChildWidth = previewWidth * height / previewHeight;
      child.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
    } else {
      final int scaledChildHeight = previewHeight * width / previewWidth;
      child.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
    }
  }

  public int getDisplayOrientation() {
    return displayOrientation;
  }

  public void setOneShotPreviewCallback(PreviewCallback callback) {
    if (camera != null) camera.setOneShotPreviewCallback(callback);
  }

  public @Nullable Camera.Parameters getCameraParameters() {
    return camera == null || !cameraReady ? null : camera.getParameters();
  }

  void previewCreated() {
    Log.w(TAG, "previewCreated() queued");
    enqueueTask(new PostInitializationTask<Void>() {
      @Override protected void onPostMain(Void avoid) {
        try {
          if (camera != null) {
            previewStrategy.attach(camera);
          }
        } catch (IOException e) {
          host.handleException(e);
        }
        Log.w(TAG, "previewCreated() completed");
      }
    });
  }

  void previewDestroyed() {
    try {
      if (camera != null) {
        stopPreview();
        camera.release();
      }
    } finally {
      camera = null;
    }
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void initPreview() {
    Log.w(TAG, "initPreview() queued");
    enqueueTask(new PostInitializationTask<Void>() {
      @Override protected void onPostMain(Void avoid) {
        if (camera != null && cameraReady) {
          Camera.Parameters parameters = camera.getParameters();

          parameters.setPreviewSize(previewSize.width, previewSize.height);

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setRecordingHint(host.getRecordingHint() != CameraHost.RecordingHint.STILL_ONLY);
          }

          camera.setParameters(host.adjustPreviewParameters(parameters));
          startPreview();
          requestLayout();
          invalidate();
          Log.w(TAG, "initPreview() completed");
        }
      }
    });
  }

  private void startPreview() {
    camera.startPreview();
    inPreview = true;
    host.autoFocusAvailable();
  }

  private void stopPreview() {
    if (inPreview) {
      camera.startPreview();
      inPreview = false;
      host.autoFocusUnavailable();
      camera.stopPreview();
    }
  }

  // based on
  // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
  // and http://stackoverflow.com/a/10383164/115145
  private void setCameraDisplayOrientation() {
    Camera.CameraInfo info     = new Camera.CameraInfo();
    int               rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
    int               degrees  = 0;
    DisplayMetrics    dm       = new DisplayMetrics();

    Camera.getCameraInfo(host.getCameraId(), info);
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
    if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      outputOrientation = getCameraPictureRotation(ServiceUtil.getWindowManager(getContext())
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

  private CameraInfo getCameraInfo() {
    final CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(host.getCameraId(), info);
    return info;
  }

  // XXX this sucks
  private Activity getActivity() {
    return (Activity)getContext();
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
        runOnMainSync(new Runnable() {
          @Override public void run() {
            onPreMain();
          }
        });

        final Result result = onRunBackground();

        runOnMainSync(new Runnable() {
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

    private void runOnMainSync(final Runnable runnable) {
      final CountDownLatch sync = new CountDownLatch(1);
      Util.runOnMain(new Runnable() {
        @Override public void run() {
          try {
            runnable.run();
          } finally {
            sync.countDown();
          }
        }
      });
      try {
        sync.await();
      } catch (InterruptedException ie) {
        throw new AssertionError(ie);
      }
    }

    protected void onWait() throws PreconditionsNotMetException {}
    protected void onPreMain() {}
    protected Result onRunBackground() { return null; }
    protected void onPostMain(Result result) {}
  }

  private abstract class PostInitializationTask<Result> extends SerialAsyncTask<Result> {
    @Override protected void onWait() throws PreconditionsNotMetException {
      synchronized (CameraView.this) {
        if (!cameraReady) {
          throw new PreconditionsNotMetException();
        }
        while (camera == null || previewSize == null || !previewStrategy.isReady()) {
          Log.w(TAG, String.format("waiting. camera? %s previewSize? %s prevewStrategy? %s",
                                   camera != null, previewSize != null, previewStrategy.isReady()));
          Util.wait(CameraView.this, 0);
        }
      }
    }
  }

  private static class PreconditionsNotMetException extends Exception {}
}
