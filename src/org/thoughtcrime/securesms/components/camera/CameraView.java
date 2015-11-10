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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ViewGroup;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorInflater;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ValueAnimator;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.jobqueue.Job;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libaxolotl.util.guava.Optional;

@SuppressWarnings("deprecation")
public class CameraView extends ViewGroup {
  private static final String TAG = CameraView.class.getSimpleName();

  private final CameraSurfaceView   surface;
  private final OnOrientationChange onOrientationChange;

  private volatile Optional<Camera> camera             = Optional.absent();
  private volatile int              cameraId           = CameraInfo.CAMERA_FACING_BACK;
  private volatile int              displayOrientation = -1;

  private @NonNull  State                    state = State.PAUSED;
  private @Nullable Size                     previewSize;
  private @NonNull  List<CameraViewListener> listeners = Collections.synchronizedList(new LinkedList<CameraViewListener>());
  private           int                      outputOrientation  = -1;

  private           int                   focusAreaSize   = -1;
  private           GestureDetectorCompat gestureDetector;
  private           boolean               focusEnabled, focusAnimationRunning, focusComplete;
  private           float                 focusCircleX, focusCircleY, focusCircleRadius;
  private           Paint                 focusCirclePaint;
  private           ValueAnimator         focusCircleExpandAnimation, focusCircleFadeOutAnimation;

  public CameraView(Context context) {
    this(context, null);
  }

  public CameraView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public CameraView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    setBackgroundColor(Color.BLACK);

    if (attrs != null) {
      TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.CameraView);
      int        camera     = typedArray.getInt(R.styleable.CameraView_camera, -1);

      if      (camera != -1)    cameraId = camera;
      else if (isMultiCamera()) cameraId = TextSecurePreferences.getDirectCaptureCameraId(context);

      typedArray.recycle();
    }

    surface             = new CameraSurfaceView(getContext());
    onOrientationChange = new OnOrientationChange(context.getApplicationContext());
    addView(surface);
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  public void onResume() {
    if (state != State.PAUSED) return;
    state = State.RESUMED;
    Log.w(TAG, "onResume() queued");
    enqueueTask(new SerialAsyncTask<Void>() {
      @Override
      protected
      @Nullable
      Void onRunBackground() {
        try {
          long openStartMillis = System.currentTimeMillis();
          camera = Optional.fromNullable(Camera.open(cameraId));
          Log.w(TAG, "camera.open() -> " + (System.currentTimeMillis() - openStartMillis) + "ms");
          synchronized (CameraView.this) {
            CameraView.this.notifyAll();
          }
          if (camera.isPresent()) onCameraReady(camera.get());
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
        } else {
          Parameters parameters = camera.get().getParameters();
          focusEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
              parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO) &&
              (parameters.getMaxNumFocusAreas() > 0 || parameters.getMaxNumMeteringAreas() > 0);
          if (focusEnabled) setupFocusAnimation();
        }

        if (getActivity().getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
          onOrientationChange.enable();
        }
        Log.w(TAG, "onResume() completed");
      }
    });
  }

  public void onPause() {
    if (state == State.PAUSED) return;
    state = State.PAUSED;
    Log.w(TAG, "onPause() queued");

    enqueueTask(new SerialAsyncTask<Void>() {
      private Optional<Camera> cameraToDestroy;

      @Override
      protected void onPreMain() {
        cameraToDestroy = camera;
        camera = Optional.absent();
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
        outputOrientation = -1;
        removeView(surface);
        addView(surface);
        Log.w(TAG, "onPause() completed");
      }
    });

    for (CameraViewListener listener : listeners) {
      listener.onCameraStop();
    }
  }

  public boolean isStarted() {
    return state != State.PAUSED;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int  width         = r - l;
    final int  height        = b - t;
    final int  previewWidth;
    final int  previewHeight;

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
    Log.w(TAG, "onSizeChanged(" + oldw + "x" + oldh + " -> " + w + "x" + h + ")");
    super.onSizeChanged(w, h, oldw, oldh);
    if (camera.isPresent()) startPreview(camera.get().getParameters());
  }

  public void addListener(@NonNull CameraViewListener listener) {
    listeners.add(listener);
  }

  public void setPreviewCallback(final @NonNull PreviewCallback previewCallback) {
    enqueueTask(new PostInitializationTask<Void>() {
      @Override
      protected void onPostMain(Void avoid) {
        if (camera.isPresent()) {
          camera.get().setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
              if (!CameraView.this.camera.isPresent()) {
                return;
              }

              final int  rotation    = getCameraPictureOrientation();
              final Size previewSize = camera.getParameters().getPreviewSize();
              if (data != null) {
                previewCallback.onPreviewFrame(new PreviewFrame(data, previewSize.width, previewSize.height, rotation));
              }
            }
          });
        }
      }
    });
  }

  public boolean isMultiCamera() {
    return Camera.getNumberOfCameras() > 1;
  }

  public boolean isRearCamera() {
    return cameraId == CameraInfo.CAMERA_FACING_BACK;
  }

  public void flipCamera() {
    if (Camera.getNumberOfCameras() > 1) {
      cameraId = cameraId == CameraInfo.CAMERA_FACING_BACK
                 ? CameraInfo.CAMERA_FACING_FRONT
                 : CameraInfo.CAMERA_FACING_BACK;
      onPause();
      onResume();
      TextSecurePreferences.setDirectCaptureCameraId(getContext(), cameraId);
    }
  }

  @TargetApi(14)
  private void onCameraReady(final @NonNull Camera camera) {
    final Parameters parameters = camera.getParameters();

    if (VERSION.SDK_INT >= 14) {
      parameters.setRecordingHint(true);
      final List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      } else if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
        parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
      }
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

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  protected boolean focusOnArea(float x, float y, @Nullable Camera.AutoFocusCallback callback) {
    if (!camera.isPresent()) return false;
    Camera cameraInstance = camera.get();
    cameraInstance.cancelAutoFocus();

    final Size previewSize = camera.get().getParameters().getPreviewSize();
    float newX = 0, newY = 0;
    if (previewSize != null && (displayOrientation == 90 || displayOrientation == 270)) {
      newX = x / previewSize.height * 2000 - 1000;
      newY = y / previewSize.width * 2000 - 1000;
    } else if (previewSize != null) {
      newX = x / previewSize.width * 2000 - 1000;
      newY = y / previewSize.height * 2000 - 1000;
    }
    Rect focusRect = calculateTapArea(newX, newY, 1.f);
    // AE area is bigger because exposure is sensitive and
    // easy to over- or underexposure if area is too small.
    Rect meteringRect = calculateTapArea(newX, newY, 1.5f);

    Camera.Parameters parameters = cameraInstance.getParameters();
    if (parameters.getMaxNumFocusAreas() > 0) {
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      ArrayList<Camera.Area> focusAreaArrayList = new ArrayList<>();
      focusAreaArrayList.add(new Camera.Area(focusRect, 1000));
      parameters.setFocusAreas(focusAreaArrayList);
    }
    if (parameters.getMaxNumMeteringAreas() > 0) {
      ArrayList<Camera.Area> meteringAreaArrayList = new ArrayList<>();
      meteringAreaArrayList.add(new Camera.Area(meteringRect, 1000));
      parameters.setMeteringAreas(meteringAreaArrayList);
    }
    cameraInstance.setParameters(parameters);
    cameraInstance.autoFocus(callback);
    return true;
  }

  private Rect calculateTapArea(float x, float y, float coefficient) {
    int scaledFocusAreaSize = (int) (coefficient * focusAreaSize);
    int left = clamp((int) x - scaledFocusAreaSize / 2, -1000, 1000 - scaledFocusAreaSize);
    int top = clamp((int) y - scaledFocusAreaSize / 2, -1000, 1000 - scaledFocusAreaSize);
    return new Rect(left, top, left + scaledFocusAreaSize, top + scaledFocusAreaSize);
  }

  private static int clamp(int x, int min, int max) {
    return Math.min(Math.max(x, min), max);
  }

  private void setupFocusAnimation() {
    TapToFocusListener tapToFocusListener = new TapToFocusListener();
    gestureDetector = new GestureDetectorCompat(getContext(), tapToFocusListener);
    gestureDetector.setIsLongpressEnabled(false);
    focusAnimationRunning = false;
    focusComplete = false;
    focusCircleX = focusCircleY = 0;
    focusCirclePaint = new Paint();
    focusCirclePaint.setAntiAlias(true);
    focusCirclePaint.setColor(Color.WHITE);
    focusCirclePaint.setStyle(Paint.Style.STROKE);
    focusCirclePaint.setStrokeWidth(5);

    focusCircleExpandAnimation = (ValueAnimator) AnimatorInflater.loadAnimator(getContext(), R.animator.quick_camera_focus_circle_expand);
    focusCircleExpandAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        focusCircleRadius = (Float) animation.getAnimatedValue();
        invalidate();
      }
    });
    focusCircleExpandAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        focusCirclePaint.setAlpha(255);
        focusAnimationRunning = true;
      }

      @Override
      public void onAnimationEnd(Animator animation) {
        if (focusComplete)
          focusCircleFadeOutAnimation.start();
      }
    });

    focusCircleFadeOutAnimation = (ValueAnimator) AnimatorInflater.loadAnimator(getContext(), R.animator.quick_camera_focus_circle_fade_out);
    focusCircleFadeOutAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        focusCirclePaint.setAlpha((int) animation.getAnimatedValue());
        invalidate();
      }
    });
    focusCircleFadeOutAnimation.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        focusAnimationRunning = false;
        invalidate();
      }
    });
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return focusEnabled && gestureDetector != null ?
        this.gestureDetector.onTouchEvent(event) :
        super.onTouchEvent(event);
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    super.dispatchDraw(canvas);
    if (focusEnabled && focusAnimationRunning)
      canvas.drawCircle(focusCircleX, focusCircleY, focusCircleRadius, focusCirclePaint);
  }

  private void startPreview(final @NonNull Parameters parameters) {
    if (this.camera.isPresent()) {
      try {
        final Camera     camera               = this.camera.get();
        final Size       preferredPreviewSize = getPreferredPreviewSize(parameters);

        if (preferredPreviewSize != null && !parameters.getPreviewSize().equals(preferredPreviewSize)) {
          Log.w(TAG, "starting preview with size " + preferredPreviewSize.width + "x" + preferredPreviewSize.height);
          if (state == State.ACTIVE) stopPreview();
          previewSize = preferredPreviewSize;
          parameters.setPreviewSize(preferredPreviewSize.width, preferredPreviewSize.height);
          camera.setParameters(parameters);
        } else {
          previewSize = parameters.getPreviewSize();
        }
        // Recommended focus area size from the manufacture is 1/8 of the image
        // width (i.e. longer edge of the image)
        focusAreaSize = Math.max(parameters.getPreviewSize().width, parameters.getPreviewSize().height) / 8;
        long previewStartMillis = System.currentTimeMillis();
        camera.startPreview();
        Log.w(TAG, "camera.startPreview() -> " + (System.currentTimeMillis() - previewStartMillis) + "ms");
        state = State.ACTIVE;
        Util.runOnMain(new Runnable() {
          @Override
          public void run() {
            requestLayout();
            for (CameraViewListener listener : listeners) {
              listener.onCameraStart();
            }
          }
        });
      } catch (Exception e) {
        Log.w(TAG, e);
      }
    }
  }

  private void stopPreview() {
    if (camera.isPresent()) {
      try {
        camera.get().stopPreview();
        state = State.RESUMED;
      } catch (Exception e) {
        Log.w(TAG, e);
      }
    }
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

  // https://github.com/WhisperSystems/Signal-Android/issues/4715
  private boolean isTroublemaker() {
    return getCameraInfo().facing == CameraInfo.CAMERA_FACING_FRONT &&
           "JWR66Y".equals(Build.DISPLAY) &&
           "yakju".equals(Build.PRODUCT);
  }

  private @NonNull CameraInfo getCameraInfo() {
    final CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);
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
      if (camera.isPresent() && orientation != ORIENTATION_UNKNOWN) {
        int newOutputOrientation = getCameraPictureRotation(orientation);

        if (newOutputOrientation != outputOrientation) {
          outputOrientation = newOutputOrientation;

          Camera.Parameters params = camera.get().getParameters();

          params.setRotation(outputOrientation);

          try {
            camera.get().setParameters(params);
          }
          catch (Exception e) {
            Log.e(TAG, "Exception updating camera parameters in orientation change", e);
          }
        }
      }
    }
  }

  public void takePicture(final Rect previewRect) {
    if (!camera.isPresent() || camera.get().getParameters() == null) {
      Log.w(TAG, "camera not in capture-ready state");
      return;
    }

    camera.get().setOneShotPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, final Camera camera) {
        final int  rotation     = getCameraPictureOrientation();
        final Size previewSize  = camera.getParameters().getPreviewSize();
        final Rect croppingRect = getCroppedRect(previewSize, previewRect, rotation);

        Log.w(TAG, "previewSize: " + previewSize.width + "x" + previewSize.height);
        Log.w(TAG, "data bytes: " + data.length);
        Log.w(TAG, "previewFormat: " + camera.getParameters().getPreviewFormat());
        Log.w(TAG, "croppingRect: " + croppingRect.toString());
        Log.w(TAG, "rotation: " + rotation);
        new CaptureTask(previewSize, rotation, croppingRect).execute(data);
      }
    });
  }

  private Rect getCroppedRect(Size cameraPreviewSize, Rect visibleRect, int rotation) {
    final int previewWidth  = cameraPreviewSize.width;
    final int previewHeight = cameraPreviewSize.height;

    if (rotation % 180 > 0) rotateRect(visibleRect);

    float scale = (float) previewWidth / visibleRect.width();
    if (visibleRect.height() * scale > previewHeight) {
      scale = (float) previewHeight / visibleRect.height();
    }
    final float newWidth  = visibleRect.width()  * scale;
    final float newHeight = visibleRect.height() * scale;
    final float centerX   = (VERSION.SDK_INT < 14 || isTroublemaker()) ? previewWidth - newWidth / 2 : previewWidth / 2;
    final float centerY   = previewHeight / 2;

    visibleRect.set((int) (centerX - newWidth  / 2),
                    (int) (centerY - newHeight / 2),
                    (int) (centerX + newWidth  / 2),
                    (int) (centerY + newHeight / 2));

    if (rotation % 180 > 0) rotateRect(visibleRect);
    return visibleRect;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void rotateRect(Rect rect) {
    rect.set(rect.top, rect.left, rect.bottom, rect.right);
  }

  private void enqueueTask(SerialAsyncTask job) {
    ApplicationContext.getInstance(getContext()).getJobManager().add(job);
  }

  private class TapToFocusListener extends GestureDetector.SimpleOnGestureListener
      implements Camera.AutoFocusCallback {
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      focusCircleX = e.getX();
      focusCircleY = e.getY();
      focusCircleExpandAnimation.cancel();
      focusCircleFadeOutAnimation.cancel();
      focusAnimationRunning = false;
      focusComplete = false;
      if (focusOnArea(focusCircleX, focusCircleY, this))
        focusCircleExpandAnimation.start();
      return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
      focusComplete = true;
      if (!focusCircleExpandAnimation.isRunning())
        focusCircleFadeOutAnimation.start();
    }
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
          throw new PreconditionsNotMetException();
        }
        while (getMeasuredHeight() <= 0 || getMeasuredWidth() <= 0 || !surface.isReady()) {
          Log.w(TAG, String.format("waiting. surface ready? %s", surface.isReady()));
          Util.wait(CameraView.this, 0);
        }
      }
    }
  }

  private class CaptureTask extends AsyncTask<byte[], Void, byte[]> {
    private final Size previewSize;
    private final int  rotation;
    private final Rect croppingRect;

    public CaptureTask(Size previewSize, int rotation, Rect croppingRect) {
      this.previewSize  = previewSize;
      this.rotation     = rotation;
      this.croppingRect = croppingRect;
    }

    @Override
    protected byte[] doInBackground(byte[]... params) {
      final byte[] data = params[0];
      try {
        return BitmapUtil.createFromNV21(data,
                                         previewSize.width,
                                         previewSize.height,
                                         rotation,
                                         croppingRect,
                                         cameraId == CameraInfo.CAMERA_FACING_FRONT);
      } catch (IOException e) {
        Log.w(TAG, e);
        return null;
      }
    }

    @Override
    protected void onPostExecute(byte[] imageBytes) {
      if (imageBytes != null) {
        for (CameraViewListener listener : listeners) {
          listener.onImageCapture(imageBytes);
        }
      }
    }
  }

  private static class PreconditionsNotMetException extends Exception {}

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

    private PreviewFrame(@NonNull byte[] data, int width, int height, int orientation) {
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
