package org.thoughtcrime.securesms.mediasend;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.view.SignalCameraView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.util.Executors;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.animation.AnimationCompleteListener;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXFlashToggleView;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations;
import org.thoughtcrime.securesms.mediasend.v2.MediaCountIndicatorButton;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.util.MemoryFileDescriptor;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.video.VideoUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Camera captured implemented using the CameraX SDK, which uses Camera2 under the hood. Should be
 * preferred whenever possible.
 */
@RequiresApi(21)
public class CameraXFragment extends LoggingFragment implements CameraFragment {

  private static final String TAG              = Log.tag(CameraXFragment.class);
  private static final String IS_VIDEO_ENABLED = "is_video_enabled";

  private SignalCameraView       camera;
  private ViewGroup              controlsContainer;
  private Controller             controller;
  private View                   selfieFlash;
  private MemoryFileDescriptor   videoFileDescriptor;

  public static CameraXFragment newInstanceForAvatarCapture() {
    CameraXFragment fragment = new CameraXFragment();
    Bundle          args     = new Bundle();

    args.putBoolean(IS_VIDEO_ENABLED, false);
    fragment.setArguments(args);

    return fragment;
  }

  public static CameraXFragment newInstance() {
    CameraXFragment fragment = new CameraXFragment();

    fragment.setArguments(new Bundle());

    return fragment;
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (getActivity() instanceof Controller) {
      this.controller = (Controller) getActivity();
    } else if (getParentFragment() instanceof Controller) {
      this.controller = (Controller) getParentFragment();
    }

    if (controller == null) {
      throw new IllegalStateException("Parent must implement controller interface.");
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.camerax_fragment, container, false);
  }

  @SuppressLint("MissingPermission")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    ViewGroup cameraParent = view.findViewById(R.id.camerax_camera_parent);

    this.camera            = view.findViewById(R.id.camerax_camera);
    this.controlsContainer = view.findViewById(R.id.camerax_controls_container);

    camera.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    camera.bindToLifecycle(getViewLifecycleOwner(), this::handleCameraInitializationError);
    camera.setCameraLensFacing(CameraXUtil.toLensFacing(TextSecurePreferences.getDirectCaptureCameraId(requireContext())));

    onOrientationChanged(getResources().getConfiguration().orientation);

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

    camera.bindToLifecycle(getViewLifecycleOwner(),  this::handleCameraInitializationError);
    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    closeVideoFileDescriptor();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onOrientationChanged(newConfig.orientation);
  }

  @Override
  public void fadeOutControls(@NonNull Runnable onEndAction) {
    controlsContainer.setEnabled(false);
    controlsContainer.animate()
                     .setDuration(250)
                     .alpha(0f)
                     .setInterpolator(MediaAnimations.getInterpolator())
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
                     .setDuration(250)
                     .alpha(1f)
                     .setInterpolator(MediaAnimations.getInterpolator())
                     .setListener(new AnimationCompleteListener() {
                       @Override
                       public void onAnimationEnd(Animator animation) {
                         controlsContainer.setEnabled(true);
                       }
                     });
  }

  private void handleCameraInitializationError(Throwable error) {
    Log.w(TAG, "An error occurred", error);

    Context context = getActivity();
    if (context != null) {
      Toast.makeText(context, R.string.CameraFragment__failed_to_open_camera, Toast.LENGTH_SHORT).show();
    }
  }

  private void onOrientationChanged(int orientation) {
    int layout = orientation == Configuration.ORIENTATION_PORTRAIT ? R.layout.camera_controls_portrait
                                                                   : R.layout.camera_controls_landscape;

    controlsContainer.removeAllViews();
    controlsContainer.addView(LayoutInflater.from(getContext()).inflate(layout, controlsContainer, false));
    initControls();
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

  @SuppressLint({"ClickableViewAccessibility", "MissingPermission"})
  private void initControls() {
    View                   flipButton             = requireView().findViewById(R.id.camera_flip_button);
    CameraButtonView       captureButton          = requireView().findViewById(R.id.camera_capture_button);
    View                   galleryButton          = requireView().findViewById(R.id.camera_gallery_button);
    View                   countButton            = requireView().findViewById(R.id.camera_review_button);
    CameraXFlashToggleView flashButton            = requireView().findViewById(R.id.camera_flash_button);

    selfieFlash = requireView().findViewById(R.id.camera_selfie_flash);

    captureButton.setOnClickListener(v -> {
      captureButton.setEnabled(false);
      flipButton.setEnabled(false);
      flashButton.setEnabled(false);
      onCaptureClicked();
    });

    camera.setScaleType(PreviewView.ScaleType.FILL_CENTER);

    ProcessCameraProvider.getInstance(requireContext())
                         .addListener(() -> initializeFlipButton(flipButton, flashButton),
                                            Executors.mainThreadExecutor());

    flashButton.setAutoFlashEnabled(camera.hasFlash());
    flashButton.setFlash(camera.getFlash());
    flashButton.setOnFlashModeChangedListener(camera::setFlash);

    galleryButton.setOnClickListener(v -> controller.onGalleryClicked());
    countButton.setOnClickListener(v -> controller.onCameraCountButtonClicked());

    if (isVideoRecordingSupported(requireContext())) {
      try {
        closeVideoFileDescriptor();
        videoFileDescriptor = CameraXVideoCaptureHelper.createFileDescriptor(requireContext());

        Animation inAnimation  = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in);
        Animation outAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out);

        camera.setCaptureMode(SignalCameraView.CaptureMode.MIXED);

        int maxDuration = VideoUtil.getMaxVideoRecordDurationInSeconds(requireContext(), controller.getMediaConstraints());
        Log.d(TAG, "Max duration: " + maxDuration + " sec");

        captureButton.setVideoCaptureListener(new CameraXVideoCaptureHelper(
            this,
            captureButton,
            camera,
            videoFileDescriptor,
            maxDuration,
            new CameraXVideoCaptureHelper.Callback() {
              @Override
              public void onVideoRecordStarted() {
                hideAndDisableControlsForVideoRecording(captureButton, flashButton, flipButton, outAnimation);
              }

              @Override
              public void onVideoSaved(@NonNull FileDescriptor fd) {
                showAndEnableControlsAfterVideoRecording(captureButton, flashButton, flipButton, inAnimation);
                controller.onVideoCaptured(fd);
              }

              @Override
              public void onVideoError(@Nullable Throwable cause) {
                showAndEnableControlsAfterVideoRecording(captureButton, flashButton, flipButton, inAnimation);
                controller.onVideoCaptureError();
              }
            }
        ));
        displayVideoRecordingTooltipIfNecessary(captureButton);
      } catch (IOException e) {
        Log.w(TAG, "Video capture is not supported on this device.", e);
      }
    } else {
      Log.i(TAG, "Video capture not supported. " +
                 "API: " + Build.VERSION.SDK_INT + ", " +
                 "MFD: " + MemoryFileDescriptor.supported() + ", " +
                 "Camera: " + CameraXUtil.getLowestSupportedHardwareLevel(requireContext()) + ", " +
                 "MaxDuration: " + VideoUtil.getMaxVideoRecordDurationInSeconds(requireContext(), controller.getMediaConstraints()) + " sec");
    }
  }

  private boolean isVideoRecordingSupported(@NonNull Context context) {
    return Build.VERSION.SDK_INT >= 26                           &&
           requireArguments().getBoolean(IS_VIDEO_ENABLED, true) &&
           MediaConstraints.isVideoTranscodeAvailable()          &&
           CameraXUtil.isMixedModeSupported(context)             &&
           VideoUtil.getMaxVideoRecordDurationInSeconds(context, controller.getMediaConstraints()) > 0;
  }

  private void displayVideoRecordingTooltipIfNecessary(CameraButtonView captureButton) {
    if (shouldDisplayVideoRecordingTooltip()) {
      int displayRotation = requireActivity().getWindowManager().getDefaultDisplay().getRotation();

      TooltipPopup.forTarget(captureButton)
                  .setOnDismissListener(this::neverDisplayVideoRecordingTooltipAgain)
                  .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.core_ultramarine))
                  .setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_toolbar_title))
                  .setText(R.string.CameraXFragment_tap_for_photo_hold_for_video)
                  .show(displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180 ? TooltipPopup.POSITION_ABOVE : TooltipPopup.POSITION_START);
    }
  }

  private boolean shouldDisplayVideoRecordingTooltip() {
    return !TextSecurePreferences.hasSeenVideoRecordingTooltip(requireContext()) && MediaConstraints.isVideoTranscodeAvailable();
  }

  private void neverDisplayVideoRecordingTooltipAgain() {
    Context context = getContext();
    if (context != null) {
      TextSecurePreferences.setHasSeenVideoRecordingTooltip(requireContext(), true);
    }
  }

  private void hideAndDisableControlsForVideoRecording(@NonNull View captureButton,
                                                       @NonNull View flashButton,
                                                       @NonNull View flipButton,
                                                       @NonNull Animation outAnimation)
  {
    captureButton.setEnabled(false);
    flashButton.startAnimation(outAnimation);
    flashButton.setVisibility(View.INVISIBLE);
    flipButton.startAnimation(outAnimation);
    flipButton.setVisibility(View.INVISIBLE);
  }

  private void showAndEnableControlsAfterVideoRecording(@NonNull View captureButton,
                                                        @NonNull View flashButton,
                                                        @NonNull View flipButton,
                                                        @NonNull Animation inAnimation)
  {
    requireActivity().runOnUiThread(() -> {
      captureButton.setEnabled(true);
      flashButton.startAnimation(inAnimation);
      flashButton.setVisibility(View.VISIBLE);
      flipButton.startAnimation(inAnimation);
      flipButton.setVisibility(View.VISIBLE);
    });
  }

  private void onCaptureClicked() {
    Stopwatch stopwatch = new Stopwatch("Capture");

    CameraXSelfieFlashHelper flashHelper = new CameraXSelfieFlashHelper(
        requireActivity().getWindow(),
        camera,
        selfieFlash
    );

    camera.takePicture(Executors.mainThreadExecutor(), new ImageCapture.OnImageCapturedCallback() {
      @Override
      public void onCaptureSuccess(@NonNull ImageProxy image) {
        flashHelper.endFlash();

        SimpleTask.run(CameraXFragment.this.getViewLifecycleOwner().getLifecycle(), () -> {
          stopwatch.split("captured");
          try {
            return CameraXUtil.toJpeg(image, camera.getCameraLensFacing() == CameraSelector.LENS_FACING_FRONT);
          } catch (IOException e) {
            Log.w(TAG, "Failed to encode captured image.", e);
            return null;
          } finally {
            image.close();
          }
        }, result -> {
          stopwatch.split("transformed");
          stopwatch.stop(TAG);

          if (result != null) {
            controller.onImageCaptured(result.getData(), result.getWidth(), result.getHeight());
          } else {
            controller.onCameraError();
          }
        });
      }

      @Override
      public void onError(ImageCaptureException exception) {
        Log.w(TAG, "Failed to capture image", exception);
        flashHelper.endFlash();
        controller.onCameraError();
      }
    });

    flashHelper.startFlash();
  }

  private void closeVideoFileDescriptor() {
    if (videoFileDescriptor != null) {
      try {
        videoFileDescriptor.close();
        videoFileDescriptor = null;
      } catch (IOException e) {
        Log.w(TAG, "Failed to close video file descriptor", e);
      }
    }
  }

  @SuppressLint({"MissingPermission"})
  private void initializeFlipButton(@NonNull View flipButton, @NonNull CameraXFlashToggleView flashButton) {
    if (getContext() == null) {
      Log.w(TAG, "initializeFlipButton called either before or after fragment was attached.");
      return;
    }

    if (camera.hasCameraWithLensFacing(CameraSelector.LENS_FACING_FRONT) && camera.hasCameraWithLensFacing(CameraSelector.LENS_FACING_BACK)) {
      flipButton.setVisibility(View.VISIBLE);
      flipButton.setOnClickListener(v ->  {
        camera.toggleCamera();
        TextSecurePreferences.setDirectCaptureCameraId(getContext(), CameraXUtil.toCameraDirectionInt(camera.getCameraLensFacing()));

        Animation animation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(200);
        animation.setInterpolator(new DecelerateInterpolator());
        flipButton.startAnimation(animation);
        flashButton.setAutoFlashEnabled(camera.hasFlash());
        flashButton.setFlash(camera.getFlash());
      });

      GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
          if (flipButton.isEnabled()) {
            flipButton.performClick();
          }
          return true;
        }
      });

      camera.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

    } else {
      flipButton.setVisibility(View.GONE);
    }
  }
}
