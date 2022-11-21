package org.thoughtcrime.securesms.mediasend;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations;
import org.thoughtcrime.securesms.mediasend.v2.MediaCountIndicatorButton;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.ByteArrayOutputStream;

/**
 * Camera capture implemented with the legacy camera API's. Should only be used if sdk < 21.
 */
public class Camera1Fragment extends LoggingFragment implements CameraFragment,
                                                             TextureView.SurfaceTextureListener,
                                                             Camera1Controller.EventListener
{

  private static final String TAG = Log.tag(Camera1Fragment.class);

  private TextureView                  cameraPreview;
  private ViewGroup                    controlsContainer;
  private ImageButton                  flipButton;
  private View                         captureButton;
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

    if (getActivity() instanceof Controller) {
      this.controller = (Controller) getActivity();
    } else if (getParentFragment() instanceof Controller) {
      this.controller = (Controller) getParentFragment();
    }

    if (controller == null) {
      throw new IllegalStateException("Parent must implement controller interface.");
    }

    WindowManager windowManager = ServiceUtil.getWindowManager(getActivity());
    Display       display       = windowManager.getDefaultDisplay();
    Point         displaySize   = new Point();

    display.getSize(displaySize);

    camera        = new Camera1Controller(TextSecurePreferences.getDirectCaptureCameraId(getContext()), displaySize.x, displaySize.y, this);
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

    View cameraParent = view.findViewById(R.id.camera_preview_parent);

    onOrientationChanged(getResources().getConfiguration().orientation);

    cameraPreview.setSurfaceTextureListener(this);

    GestureDetector gestureDetector = new GestureDetector(flipGestureListener);
    cameraPreview.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

    controller.getMostRecentMediaItem().observe(getViewLifecycleOwner(), this::presentRecentItemThumbnail);

    view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      // Let's assume portrait for now, so 9:16
      float aspectRatio = CameraFragment.getAspectRatioForOrientation(getResources().getConfiguration().orientation);
      float width       = right - left;
      float height      = Math.min((1f / aspectRatio) * width, bottom - top);

      ViewGroup.LayoutParams params = cameraParent.getLayoutParams();

      // If there's a mismatch...
      if (params.height != (int) height) {
        params.width  = (int) width;
        params.height = (int) height;

        cameraParent.setLayoutParams(params);
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();
    camera.initialize();

    if (cameraPreview.isAvailable()) {
      orderEnforcer.markCompleted(Stage.SURFACE_AVAILABLE);
    }

    if (properties != null) {
      orderEnforcer.markCompleted(Stage.CAMERA_PROPERTIES_AVAILABLE);
    }

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
    orderEnforcer.reset();
  }

  @Override
  public void fadeOutControls(@NonNull Runnable onEndAction) {
    controlsContainer.setEnabled(false);
    controlsContainer.animate()
                     .setInterpolator(MediaAnimations.getInterpolator())
                     .setDuration(250)
                     .alpha(0f)
                     .setListener(new AnimationCompleteListener() {
                       @Override
                       public void onAnimationEnd(Animator animation) {
                         controlsContainer.setEnabled(true);
                         onEndAction.run();
                       }
                     });
  }

  @Override
  public void fadeInControls() {
    controlsContainer.setEnabled(false);
    controlsContainer.animate()
                     .setInterpolator(MediaAnimations.getInterpolator())
                     .setDuration(250)
                     .alpha(1f)
                     .setListener(new AnimationCompleteListener() {
                       @Override
                       public void onAnimationEnd(Animator animation) {
                         controlsContainer.setEnabled(true);
                       }
                     });
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onOrientationChanged(newConfig.orientation);
  }

  @Override
  public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    Log.d(TAG, "onSurfaceTextureAvailable");
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

  private void presentRecentItemThumbnail(Optional<Media> media) {
    if (media == null) {
      return;
    }

    ImageView thumbnail = controlsContainer.findViewById(R.id.camera_gallery_button);

    if (media.isPresent()) {
      thumbnail.setVisibility(View.VISIBLE);
      Glide.with(this)
           .load(new DecryptableUri(media.get().getUri()))
           .centerCrop()
           .into(thumbnail);
    } else {
      thumbnail.setVisibility(View.GONE);
      thumbnail.setImageResource(0);
    }
  }

  @Override
  public void presentHud(int selectedMediaCount) {
    MediaCountIndicatorButton countButton = controlsContainer.findViewById(R.id.camera_review_button);

    if (selectedMediaCount > 0) {
      countButton.setVisibility(View.VISIBLE);
      countButton.setCount(selectedMediaCount);
    } else {
      countButton.setVisibility(View.GONE);
    }
  }

  private void initControls() {
    flipButton    = requireView().findViewById(R.id.camera_flip_button);
    captureButton = requireView().findViewById(R.id.camera_capture_button);

    View galleryButton = requireView().findViewById(R.id.camera_gallery_button);
    View countButton   = requireView().findViewById(R.id.camera_review_button);

    captureButton.setOnClickListener(v -> {
      captureButton.setEnabled(false);
      onCaptureClicked();
    });

    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, () -> {
      if (properties.getCameraCount() > 1) {
        flipButton.setVisibility(properties.getCameraCount() > 1 ? View.VISIBLE : View.GONE);
        flipButton.setOnClickListener(v ->  {
          int newCameraId = camera.flip();
          TextSecurePreferences.setDirectCaptureCameraId(getContext(), newCameraId);

          Animation animation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
          animation.setDuration(200);
          animation.setInterpolator(new DecelerateInterpolator());
          flipButton.startAnimation(animation);
        });
      } else {
        flipButton.setVisibility(View.GONE);
      }
    });

    galleryButton.setOnClickListener(v -> controller.onGalleryClicked());
    countButton.setOnClickListener(v -> controller.onCameraCountButtonClicked());
  }

  private void onCaptureClicked() {
    orderEnforcer.reset();

    Stopwatch fastCaptureTimer = new Stopwatch("Capture");

    camera.capture((jpegData, frontFacing) -> {
      fastCaptureTimer.split("captured");

      Transformation<Bitmap> transformation = frontFacing ? new MultiTransformation<>(new CenterCrop(), new FlipTransformation())
                                                          : new CenterCrop();

      GlideApp.with(this)
              .asBitmap()
              .load(jpegData)
              .transform(transformation)
              .override(cameraPreview.getWidth(), cameraPreview.getHeight())
              .into(new SimpleTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                  fastCaptureTimer.split("transform");

                  ByteArrayOutputStream stream = new ByteArrayOutputStream();
                  resource.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                  fastCaptureTimer.split("compressed");

                  byte[] data = stream.toByteArray();
                  fastCaptureTimer.split("bytes");
                  fastCaptureTimer.stop(TAG);

                  controller.onImageCaptured(data, resource.getWidth(), resource.getHeight());
                }

                @Override
                public void onLoadFailed(@Nullable Drawable errorDrawable) {
                  controller.onCameraError();
                }
              });
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

    float camWidth  = isPortrait() ? Math.min(cameraPreview.getWidth(), cameraPreview.getHeight()) : Math.max(cameraPreview.getWidth(), cameraPreview.getHeight());
    float camHeight = isPortrait() ? Math.max(cameraPreview.getWidth(), cameraPreview.getHeight()) : Math.min(cameraPreview.getWidth(), cameraPreview.getHeight());

    matrix.setScale(scale.x, scale.y);
    matrix.postTranslate((camWidth - (camWidth * scale.x)) / 2, (camHeight - (camHeight * scale.y)) / 2);
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

  private enum Stage {
    SURFACE_AVAILABLE, CAMERA_PROPERTIES_AVAILABLE
  }
}
