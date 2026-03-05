/*
  Copyright (c) 2013-2014 CommonsWare, LLC
  Portions Copyright (C) 2007 The Android Open Source Project
  <p>
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

package org.signal.qr.kitkat;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.OrientationEventListener;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("deprecation")
public class QrCameraView extends ViewGroup {
  private static final String TAG = Log.tag(QrCameraView.class);

  private final CameraSurfaceView   surface;
  private final OnOrientationChange onOrientationChange;

  private volatile Optional<Camera> camera             = Optional.empty();
  private volatile int              cameraId           = CameraInfo.CAMERA_FACING_BACK;
  private volatile int              displayOrientation = -1;

  private @NonNull  State                    state             = State.PAUSED;
  private @Nullable Size                     previewSize;
  private final     List<CameraViewListener> listeners         = Collections.synchronizedList(new LinkedList<>());
  private           int                      outputOrientation = -1;

  public QrCameraView(Context context) {
    this(context, null);
  }

  public QrCameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QrCameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setBackgroundColor(Color.BLACK);

    surface             = new CameraSurfaceView(getContext());
    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
    addView(surface);
  }

  public void onResume() {
    if (state != State.PAUSED) return;
    state = State.RESUMED;
    Log.i(TAG, "onResume() queued");
    enqueueTask(new SerialAsyncTask<Void>() {
      @Override
      protected
      @Nullable
      Void onRunBackground() {
        try {
          long openStartMillis = System.currentTimeMillis();
          camera = Optional.ofNullable(Camera.open(cameraId));
          Log.i(TAG, "camera.open() -> " + (System.currentTimeMillis() - openStartMillis) + "ms");
          synchronized (QrCameraView.this) {
            QrCameraView.this.notifyAll();
          }
          camera.ifPresent(value -> onCameraReady(value));
        } catch (Exception e) {
          Log.w(TAG, e);
        }
        return null;
      }

      @Override
      protected void onPostMain(Void avoid) {
        if (!camera.isPresent()) {
          Log.w(TAG, "tried to open camera but got null");
          for (CameraViewListener listener : listeners) {
            listener.onCameraFail();
          }
          return;
        }

        if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
          onOrientationChange.enable();
        }
        Log.i(TAG, "onResume() completed");
      }
    });
  }

  public void onPause() {
    if (state == State.PAUSED) return;
    state = State.PAUSED;
    Log.i(TAG, "onPause() queued");

    enqueueTask(new SerialAsyncTask<Void>() {
      private Optional<Camera> cameraToDestroy;

      @Override
      protected void onPreMain() {
        cameraToDestroy = camera;
        camera          = Optional.empty();
      }

      @Override
      protected Void onRunBackground() {
        if (cameraToDestroy.isPresent()) {
          try {
            stopPreview();
            cameraToDestroy.get().setPreviewCallback(null);
            cameraToDestroy.get().release();
            Log.w(TAG, "released old camera instance");
          } catch (Exception e) {
            Log.w(TAG, e);
          }
        }
        return null;
      }

      @Override protected void onPostMain(Void avoid) {
        onOrientationChange.disable();
        displayOrientation = -1;
        outputOrientation  = -1;
        removeView(surface);
        addView(surface);
        Log.i(TAG, "onPause() completed");
      }
    });

    for (CameraViewListener listener : listeners) {
      listener.onCameraStop();
    }
  }

  public boolean isStarted() {
    return state != State.PAUSED;
  }

  public void toggleCamera() {
    if (cameraId == CameraInfo.CAMERA_FACING_BACK) {
      cameraId = CameraInfo.CAMERA_FACING_FRONT;
    } else {
      cameraId = CameraInfo.CAMERA_FACING_BACK;
    }
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int width  = r - l;
    final int height = b - t;
    final int previewWidth;
    final int previewHeight;

    if (camera.isPresent() && previewSize != null) {
      if (displayOrientation == 90 || displayOrientation == 270) {
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

    if (previewHeight == 0 || previewWidth == 0) {
      Log.w(TAG, "skipping layout due to zero-width/height preview size");
      return;
    }

    if (width * previewHeight > height * previewWidth) {
      final int scaledChildHeight = previewHeight * width / previewWidth;
      surface.layout(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
    } else {
      final int scaledChildWidth = previewWidth * height / previewHeight;
      surface.layout((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    Log.i(TAG, "onSizeChanged(" + oldw + "x" + oldh + " -> " + w + "x" + h + ")");
    super.onSizeChanged(w, h, oldw, oldh);
    camera.ifPresent(value -> startPreview(value.getParameters()));
  }

  public void addListener(@NonNull CameraViewListener listener) {
    listeners.add(listener);
  }

  public void setPreviewCallback(final @NonNull PreviewCallback previewCallback) {
    enqueueTask(new PostInitializationTask<Void>() {
      @Override
      protected void onPostMain(Void avoid) {
        camera.ifPresent(value -> value.setPreviewCallback((data, camera) -> {
          if (!QrCameraView.this.camera.isPresent()) {
            return;
          }

          final int  rotation    = getCameraPictureOrientation();
          final Size previewSize = camera.getParameters().getPreviewSize();
          if (data != null) {
            previewCallback.onPreviewFrame(new PreviewFrame(data, previewSize.width, previewSize.height, rotation));
          }
        }));
      }
    });
  }

  private void onCameraReady(final @NonNull Camera camera) {
    final Parameters parameters = camera.getParameters();

    parameters.setRecordingHint(true);
    final List<String> focusModes = parameters.getSupportedFocusModes();
    if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
      parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    } else if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
      parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    displayOrientation = CameraUtils.getCameraDisplayOrientation(getActivity(), getCameraInfo());
    camera.setDisplayOrientation(displayOrientation);
    camera.setParameters(parameters);
    enqueueTask(new PostInitializationTask<Void>() {
      @Override
      protected Void onRunBackground() {
        try {
          camera.setPreviewDisplay(surface.getHolder());
          startPreview(parameters);
        } catch (Exception e) {
          Log.w(TAG, "couldn't set preview display", e);
        }
        return null;
      }
    });
  }

  private void startPreview(final @NonNull Parameters parameters) {
    camera.ifPresent(camera -> {
      try {
        final Size preferredPreviewSize = getPreferredPreviewSize(parameters);

        if (preferredPreviewSize != null && !parameters.getPreviewSize().equals(preferredPreviewSize)) {
          Log.i(TAG, "starting preview with size " + preferredPreviewSize.width + "x" + preferredPreviewSize.height);
          if (state == State.ACTIVE) stopPreview();
          previewSize = preferredPreviewSize;
          parameters.setPreviewSize(preferredPreviewSize.width, preferredPreviewSize.height);
          camera.setParameters(parameters);
        } else {
          previewSize = parameters.getPreviewSize();
        }
        long previewStartMillis = System.currentTimeMillis();
        camera.startPreview();
        Log.i(TAG, "camera.startPreview() -> " + (System.currentTimeMillis() - previewStartMillis) + "ms");
        state = State.ACTIVE;
        ThreadUtil.runOnMain(() -> {
          requestLayout();
          for (CameraViewListener listener : listeners) {
            listener.onCameraStart();
          }
        });
      } catch (Exception e) {
        Log.w(TAG, e);
      }
    });
  }

  private void stopPreview() {
    camera.ifPresent(camera -> {
      try {
        camera.stopPreview();
        state = State.RESUMED;
      } catch (Exception e) {
        Log.w(TAG, e);
      }
    });
  }

  private Size getPreferredPreviewSize(@NonNull Parameters parameters) {
    return CameraUtils.getPreferredPreviewSize(displayOrientation,
                                               getMeasuredWidth(),
                                               getMeasuredHeight(),
                                               parameters);
  }

  private int getCameraPictureOrientation() {
    if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
      outputOrientation = getCameraPictureRotation(getActivity().getWindowManager()
                                                                .getDefaultDisplay()
                                                                .getOrientation());
    } else if (getCameraInfo().facing == CameraInfo.CAMERA_FACING_FRONT) {
      outputOrientation = (360 - displayOrientation) % 360;
    } else {
      outputOrientation = displayOrientation;
    }

    return outputOrientation;
  }

  private @NonNull CameraInfo getCameraInfo() {
    final CameraInfo info = new CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    return info;
  }

  // XXX this sucks
  private Activity getActivity() {
    return (Activity) getContext();
  }

  public int getCameraPictureRotation(int orientation) {
    final CameraInfo info = getCameraInfo();
    final int        rotation;

    orientation = (orientation + 45) / 90 * 90;

    if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
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
      camera.ifPresent(camera -> {
        if (orientation != ORIENTATION_UNKNOWN) {
          int newOutputOrientation = getCameraPictureRotation(orientation);

          if (newOutputOrientation != outputOrientation) {
            outputOrientation = newOutputOrientation;

            Parameters params = camera.getParameters();

            params.setRotation(outputOrientation);

            try {
              camera.setParameters(params);
            } catch (Exception e) {
              Log.e(TAG, "Exception updating camera parameters in orientation change", e);
            }
          }
        }
      });
    }
  }

  private void enqueueTask(SerialAsyncTask<?> job) {
    AsyncTask.SERIAL_EXECUTOR.execute(job);
  }

  public static abstract class SerialAsyncTask<Result> implements Runnable {

    @Override
    public final void run() {
      if (!onWait()) {
        Log.w(TAG, "skipping task, preconditions not met in onWait()");
        return;
      }

      ThreadUtil.runOnMainSync(this::onPreMain);
      final Result result = onRunBackground();
      ThreadUtil.runOnMainSync(() -> onPostMain(result));
    }

    protected boolean onWait() {return true;}

    protected void onPreMain() {}

    protected Result onRunBackground() {return null;}

    protected void onPostMain(Result result) {}
  }

  private abstract class PostInitializationTask<Result> extends SerialAsyncTask<Result> {
    @Override protected boolean onWait() {
      synchronized (QrCameraView.this) {
        if (!camera.isPresent()) {
          return false;
        }
        while (getMeasuredHeight() <= 0 || getMeasuredWidth() <= 0 || !surface.isReady()) {
          Log.i(TAG, String.format("waiting. surface ready? %s", surface.isReady()));
          waitFor();
        }
        return true;
      }
    }
  }

  private void waitFor() {
    try {
      wait(0);
    } catch (InterruptedException ie) {
      throw new AssertionError(ie);
    }
  }

  public interface CameraViewListener {
    void onImageCapture(@NonNull final byte[] imageBytes);

    void onCameraFail();

    void onCameraStart();

    void onCameraStop();
  }

  public interface PreviewCallback {
    void onPreviewFrame(@NonNull PreviewFrame frame);
  }

  public static class PreviewFrame {
    private final @NonNull byte[] data;
    private final          int    width;
    private final          int    height;
    private final          int    orientation;

    public PreviewFrame(@NonNull byte[] data, int width, int height, int orientation) {
      this.data        = data;
      this.width       = width;
      this.height      = height;
      this.orientation = orientation;
    }

    public @NonNull byte[] getData() {
      return data;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }

    public int getOrientation() {
      return orientation;
    }
  }

  private enum State {
    PAUSED, RESUMED, ACTIVE
  }
}
