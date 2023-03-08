package org.thoughtcrime.securesms.mediasend;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.card.MaterialCardView;

import org.signal.core.util.Stopwatch;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXModelBlocklist;
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations;
import org.thoughtcrime.securesms.mediasend.v2.MediaCountIndicatorButton;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.io.ByteArrayOutputStream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Camera capture implemented with the legacy camera API's. Should only be used if a device is on the {@link CameraXModelBlocklist}.
 */
public class Camera1Fragment extends LoggingFragment implements CameraFragment,
                                                                TextureView.SurfaceTextureListener,
                                                                Camera1Controller.EventListener
{

  private static final String TAG = Log.tag(Camera1Fragment.class);

  private TextureView                      cameraPreview;
  private ViewGroup                        controlsContainer;
  private ImageButton                      flipButton;
  private View                             captureButton;
  private Camera1Controller                camera;
  private Controller                       controller;
  private OrderEnforcer<Stage>             orderEnforcer;
  private Camera1Controller.Properties     properties;
  private RotationListener                 rotationListener;
  private Disposable                       rotationListenerDisposable;
  private Disposable                       mostRecentItemDisposable = Disposable.disposed();
  private CameraScreenBrightnessController cameraScreenBrightnessController;
  private boolean                          isMediaSelected;

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
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    cameraScreenBrightnessController = new CameraScreenBrightnessController(requireActivity().getWindow(), new CameraStateProvider(camera));
    getViewLifecycleOwner().getLifecycle().addObserver(cameraScreenBrightnessController);

    rotationListener  = new RotationListener(requireContext());
    cameraPreview     = view.findViewById(R.id.camera_preview);
    controlsContainer = view.findViewById(R.id.camera_controls_container);

    View cameraParent = view.findViewById(R.id.camera_preview_parent);

    onOrientationChanged();

    cameraPreview.setSurfaceTextureListener(this);

    GestureDetector gestureDetector = new GestureDetector(flipGestureListener);
    cameraPreview.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

    view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
      // Let's assume portrait for now, so 9:16
      float aspectRatio = CameraFragment.getAspectRatioForOrientation(Configuration.ORIENTATION_PORTRAIT);
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
    });

    rotationListenerDisposable = rotationListener.getObservable()
                                                 .distinctUntilChanged()
                                                 .filter(rotation -> rotation != RotationListener.Rotation.ROTATION_180)
                                                 .subscribe(rotation -> {
                                                   orderEnforcer.run(Stage.SURFACE_AVAILABLE, () -> {
                                                     camera.setScreenRotation(rotation.getSurfaceRotation());
                                                   });
                                                 });

    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, this::updatePreviewScale);
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
  }

  @Override
  public void onPause() {
    super.onPause();
    rotationListenerDisposable.dispose();
    camera.release();
    orderEnforcer.reset();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mostRecentItemDisposable.dispose();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
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
  public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    Log.d(TAG, "onSurfaceTextureAvailable");
    orderEnforcer.markCompleted(Stage.SURFACE_AVAILABLE);
  }

  @Override
  public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
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

  private void presentRecentItemThumbnail(@Nullable Media media) {
    View      thumbBackground = controlsContainer.findViewById(R.id.camera_gallery_button_background);
    ImageView thumbnail       = controlsContainer.findViewById(R.id.camera_gallery_button);

    if (media != null) {
      thumbBackground.setBackgroundResource(R.drawable.circle_tintable);
      thumbnail.clearColorFilter();
      thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
      Glide.with(this)
           .load(new DecryptableUri(media.getUri()))
           .centerCrop()
           .into(thumbnail);
    } else {
      thumbBackground.setBackgroundResource(R.drawable.media_selection_camera_switch_background);
      thumbnail.setImageResource(R.drawable.symbol_album_tilt_24);
      thumbnail.setColorFilter(Color.WHITE);
      thumbnail.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    }
  }

  @Override
  public void presentHud(int selectedMediaCount) {
    MediaCountIndicatorButton countButton            = controlsContainer.findViewById(R.id.camera_review_button);
    View                      cameraGalleryContainer = controlsContainer.findViewById(R.id.camera_gallery_button_background);

    if (selectedMediaCount > 0) {
      countButton.setVisibility(View.VISIBLE);
      countButton.setCount(selectedMediaCount);
      cameraGalleryContainer.setVisibility(View.GONE);
    } else {
      countButton.setVisibility(View.GONE);
      cameraGalleryContainer.setVisibility(View.VISIBLE);
    }

    isMediaSelected = selectedMediaCount > 0;
    updateGalleryVisibility();
  }

  private void updateGalleryVisibility() {
    View cameraGalleryContainer = controlsContainer.findViewById(R.id.camera_gallery_button_background);

    if (isMediaSelected) {
      cameraGalleryContainer.setVisibility(View.GONE);
    } else {
      cameraGalleryContainer.setVisibility(View.VISIBLE);
    }
  }

  private void initControls() {
    flipButton    = requireView().findViewById(R.id.camera_flip_button);
    captureButton = requireView().findViewById(R.id.camera_capture_button);

    View galleryButton = requireView().findViewById(R.id.camera_gallery_button);
    View countButton   = requireView().findViewById(R.id.camera_review_button);

    mostRecentItemDisposable.dispose();
    mostRecentItemDisposable = controller.getMostRecentMediaItem()
                                         .observeOn(AndroidSchedulers.mainThread())
                                         .subscribe(item -> presentRecentItemThumbnail(item.orElse(null)));

    initializeViewFinderAndControlsPositioning();

    captureButton.setOnClickListener(v -> {
      captureButton.setEnabled(false);
      onCaptureClicked();
    });

    orderEnforcer.run(Stage.CAMERA_PROPERTIES_AVAILABLE, () -> {
      if (properties.getCameraCount() > 1) {
        flipButton.setVisibility(properties.getCameraCount() > 1 ? View.VISIBLE : View.GONE);
        flipButton.setOnClickListener(v -> {
          int newCameraId = camera.flip();
          TextSecurePreferences.setDirectCaptureCameraId(getContext(), newCameraId);

          Animation animation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
          animation.setDuration(200);
          animation.setInterpolator(new DecelerateInterpolator());
          flipButton.startAnimation(animation);
          cameraScreenBrightnessController.onCameraDirectionChanged(newCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT);
        });
      } else {
        flipButton.setVisibility(View.GONE);
      }
    });

    galleryButton.setOnClickListener(v -> controller.onGalleryClicked());
    countButton.setOnClickListener(v -> controller.onCameraCountButtonClicked());
  }

  private void initializeViewFinderAndControlsPositioning() {
    MaterialCardView cameraCard = requireView().findViewById(R.id.camera_preview_parent);
    View             controls   = requireView().findViewById(R.id.camera_controls_container);
    CameraDisplay    cameraDisplay = CameraDisplay.getDisplay(requireActivity());

    if (!cameraDisplay.getRoundViewFinderCorners()) {
      cameraCard.setRadius(0f);
    }

    ViewUtil.setBottomMargin(controls, cameraDisplay.getCameraCaptureMarginBottom(getResources()));

    if (cameraDisplay.getCameraViewportGravity() == CameraDisplay.CameraViewportGravity.CENTER) {
      ConstraintSet constraintSet = new ConstraintSet();
      constraintSet.clone((ConstraintLayout) requireView());
      constraintSet.connect(R.id.camera_preview_parent, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
      constraintSet.applyTo((ConstraintLayout) requireView());
    } else {
      ViewUtil.setBottomMargin(cameraCard, cameraDisplay.getCameraViewportMarginBottom());
    }
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

  private void onOrientationChanged() {
    int layout = R.layout.camera_controls_portrait;

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

  private static class CameraStateProvider implements CameraScreenBrightnessController.CameraStateProvider {

    private final Camera1Controller camera1Controller;

    private CameraStateProvider(Camera1Controller camera1Controller) {
      this.camera1Controller = camera1Controller;
    }

    @Override
    public boolean isFrontFacingCameraSelected() {
      return camera1Controller.isCameraFacingFront();
    }

    @Override
    public boolean isFlashEnabled() {
      return false;
    }
  }
}
