package org.thoughtcrime.securesms.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.LifecycleBoundTask;

import java.io.ByteArrayOutputStream;

public class Camera1Fragment extends Fragment implements TextureView.SurfaceTextureListener,
                                                         Camera1Controller.EventListener
{

  private static final String TAG = Camera1Fragment.class.getSimpleName();

  private TextureView                  cameraPreview;
  private ViewGroup                    controlsContainer;
  private ImageButton                  flipButton;
  private Button                       captureButton;
  private Camera1Controller            camera;
  private Controller                   controller;
  private OrderEnforcer<Stage>         orderEnforcer;
  private Camera1Controller.Properties properties;

  public static Camera1Fragment newInstance() {
    return new Camera1Fragment();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement the Controller interface.");
    }

    controller    = (Controller) getActivity();
    camera        = new Camera1Controller(TextSecurePreferences.getDirectCaptureCameraId(getContext()), this);
    orderEnforcer = new OrderEnforcer<>(Stage.SURFACE_AVAILABLE, Stage.CAMERA_PROPERTIES_AVAILABLE);
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.camera_fragment, container, false);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    cameraPreview     = view.findViewById(R.id.camera_preview);
    controlsContainer = view.findViewById(R.id.camera_controls_container);

    onOrientationChanged(getResources().getConfiguration().orientation);

    cameraPreview.setSurfaceTextureListener(this);

    GestureDetector gestureDetector = new GestureDetector(flipGestureListener);
    cameraPreview.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
  }

  @Override
  public void onResume() {
    super.onResume();
    camera.initialize();
    orderEnforcer.run(Stage.SURFACE_AVAILABLE, () -> {
      camera.linkSurface(cameraPreview.getSurfaceTexture());
      camera.setScreenRotation(controller.getDisplayRotation());
    });
    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, this::updatePreviewScale);
  }

  @Override
  public void onPause() {
    super.onPause();
    camera.release();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onOrientationChanged(newConfig.orientation);
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    orderEnforcer.markCompleted(Stage.SURFACE_AVAILABLE);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    orderEnforcer.run(Stage.SURFACE_AVAILABLE, () -> camera.setScreenRotation(controller.getDisplayRotation()));
    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, this::updatePreviewScale);
  }

  @Override
  public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
    return false;
  }

  @Override
  public void onSurfaceTextureUpdated(SurfaceTexture surface) {
  }

  @Override
  public void onPropertiesAvailable(@NonNull Camera1Controller.Properties properties) {
    Log.d(TAG, "Got camera properties: " + properties);
    this.properties = properties;
    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, this::updatePreviewScale);
    orderEnforcer.markCompleted(Stage.CAMERA_PROPERTIES_AVAILABLE);
  }

  @Override
  public void onCameraUnavailable() {
    controller.onCameraError();
  }

  @SuppressLint("ClickableViewAccessibility")
  private void initControls() {
    flipButton    = getView().findViewById(R.id.camera_flip_button);
    captureButton = getView().findViewById(R.id.camera_capture_button);

    captureButton.setOnTouchListener((v, event) -> {
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          Animation shrinkAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.camera_capture_button_shrink);
          shrinkAnimation.setFillAfter(true);
          shrinkAnimation.setFillEnabled(true);
          captureButton.startAnimation(shrinkAnimation);
          onCaptureClicked();
          break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
        case MotionEvent.ACTION_OUTSIDE:
          Animation growAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.camera_capture_button_grow);
          growAnimation.setFillAfter(true);
          growAnimation.setFillEnabled(true);
          captureButton.startAnimation(growAnimation);
          captureButton.setEnabled(false);
          break;
      }
      return true;
    });

    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, () -> {
      if (properties.getCameraCount() > 1) {
        flipButton.setVisibility(properties.getCameraCount() > 1 ? View.VISIBLE : View.GONE);
        flipButton.setImageResource(TextSecurePreferences.getDirectCaptureCameraId(getContext()) == Camera.CameraInfo.CAMERA_FACING_BACK ? R.drawable.ic_camera_front
                                                                                                                                         : R.drawable.ic_camera_rear);
        flipButton.setOnClickListener(v ->  {
          int newCameraId = camera.flip();
          flipButton.setImageResource(newCameraId == Camera.CameraInfo.CAMERA_FACING_BACK ? R.drawable.ic_camera_front
                                                                                          : R.drawable.ic_camera_rear);

          TextSecurePreferences.setDirectCaptureCameraId(getContext(), newCameraId);
        });
      } else {
        flipButton.setVisibility(View.GONE);
      }
    });
  }

  private void onCaptureClicked() {
    orderEnforcer.reset();

    Stopwatch fastCaptureTimer = new Stopwatch("Fast Capture");

    Bitmap preview = cameraPreview.getBitmap();
    fastCaptureTimer.split("captured");

    LifecycleBoundTask.run(getLifecycle(), () -> {
      Bitmap full = preview;
      if (Build.VERSION.SDK_INT < 28) {
        PointF scale  = getScaleTransform(cameraPreview.getWidth(), cameraPreview.getHeight(), properties.getPreviewWidth(), properties.getPreviewHeight());
        Matrix matrix = new Matrix();

        matrix.setScale(scale.x, scale.y);

        int adjWidth  = (int) (cameraPreview.getWidth() / scale.x);
        int adjHeight = (int) (cameraPreview.getHeight() / scale.y);

        full = Bitmap.createBitmap(preview, 0, 0, adjWidth, adjHeight, matrix, true);
      }

      fastCaptureTimer.split("transformed");

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      full.compress(Bitmap.CompressFormat.JPEG, 80, stream);
      fastCaptureTimer.split("compressed");

      byte[] data = stream.toByteArray();
      fastCaptureTimer.split("bytes");
      fastCaptureTimer.stop(TAG);

      return data;
    }, data -> {
      if (data != null) {
        controller.onImageCaptured(data);
      } else {
        controller.onCameraError();
      }
    });
  }

  private PointF getScaleTransform(float viewWidth, float viewHeight, int cameraWidth, int cameraHeight) {
    float camWidth  = isPortrait() ? Math.min(cameraWidth, cameraHeight) : Math.max(cameraWidth, cameraHeight);
    float camHeight = isPortrait() ? Math.max(cameraWidth, cameraHeight) : Math.min(cameraWidth, cameraHeight);

    float scaleX = 1;
    float scaleY = 1;

    if ((camWidth / viewWidth) > (camHeight / viewHeight)) {
      float targetWidth = viewHeight * (camWidth / camHeight);
      scaleX = targetWidth / viewWidth;
    } else {
      float targetHeight = viewWidth * (camHeight / camWidth);
      scaleY = targetHeight / viewHeight;
    }

    return new PointF(scaleX, scaleY);
  }

  private void onOrientationChanged(int orientation) {
    int layout = orientation == Configuration.ORIENTATION_PORTRAIT ? R.layout.camera_controls_portrait
                                                                   : R.layout.camera_controls_landscape;

    controlsContainer.removeAllViews();
    controlsContainer.addView(LayoutInflater.from(getContext()).inflate(layout, controlsContainer, false));
    initControls();
  }

  private void updatePreviewScale() {
    PointF scale  = getScaleTransform(cameraPreview.getWidth(), cameraPreview.getHeight(), properties.getPreviewWidth(), properties.getPreviewHeight());
    Matrix matrix = new Matrix();

    matrix.setScale(scale.x, scale.y);
    cameraPreview.setTransform(matrix);
  }

  private boolean isPortrait() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
  }

  private final GestureDetector.OnGestureListener flipGestureListener = new GestureDetector.SimpleOnGestureListener() {
    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
      flipButton.performClick();
      return true;
    }
  };

  public interface Controller {
    void onCameraError();
    void onImageCaptured(@NonNull byte[] data);
    int getDisplayRotation();
  }

  private enum Stage {
    SURFACE_AVAILABLE, CAMERA_PROPERTIES_AVAILABLE
  }
}
