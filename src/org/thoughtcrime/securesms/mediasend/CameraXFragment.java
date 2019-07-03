package org.thoughtcrime.securesms.mediasend;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXView;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;

/**
 * Camera captured implemented using the CameraX SDK, which uses Camera2 under the hood. Should be
 * preferred whenever possible.
 */
@RequiresApi(21)
public class CameraXFragment extends Fragment implements CameraFragment {

  private static final String TAG = Log.tag(CameraXFragment.class);

  private CameraXView        camera;
  private ViewGroup          controlsContainer;
  private Controller         controller;
  private MediaSendViewModel viewModel;

  public static CameraXFragment newInstance() {
    return new CameraXFragment();
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (!(getActivity() instanceof Controller)) {
      throw new IllegalStateException("Parent activity must implement controller interface.");
    }

    this.controller = (Controller) getActivity();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    viewModel = ViewModelProviders.of(requireActivity(), new MediaSendViewModel.Factory(requireActivity().getApplication(), new MediaRepository()))
                                  .get(MediaSendViewModel.class);
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.camerax_fragment, container, false);
  }

  @SuppressLint("MissingPermission")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    this.camera            = view.findViewById(R.id.camerax_camera);
    this.controlsContainer = view.findViewById(R.id.camerax_controls_container);

    camera.bindToLifecycle(this);
    camera.setCameraLensFacing(CameraXUtil.toLensFacing(TextSecurePreferences.getDirectCaptureCameraId(requireContext())));

    onOrientationChanged(getResources().getConfiguration().orientation);

    viewModel.getMostRecentMediaItem(requireContext()).observe(this, this::presentRecentItemThumbnail);
    viewModel.getHudState().observe(this, this::presentHud);
  }

  @Override
  public void onResume() {
    super.onResume();

    viewModel.onCameraStarted();
    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    CameraX.unbindAll();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onOrientationChanged(newConfig.orientation);
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

  private void presentHud(@Nullable MediaSendViewModel.HudState state) {
    if (state == null) return;

    View     countButton     = controlsContainer.findViewById(R.id.camera_count_button);
    TextView countButtonText = controlsContainer.findViewById(R.id.mediasend_count_button_text);

    if (state.getButtonState() == MediaSendViewModel.ButtonState.COUNT) {
      countButton.setVisibility(View.VISIBLE);
      countButtonText.setText(String.valueOf(state.getSelectionCount()));
    } else {
      countButton.setVisibility(View.GONE);
    }
  }

  @SuppressLint({"ClickableViewAccessibility", "MissingPermission"})
  private void initControls() {
    View flipButton    = requireView().findViewById(R.id.camera_flip_button);
    View captureButton = requireView().findViewById(R.id.camera_capture_button);
    View galleryButton = requireView().findViewById(R.id.camera_gallery_button);
    View countButton   = requireView().findViewById(R.id.camera_count_button);

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

    if (camera.hasCameraWithLensFacing(CameraX.LensFacing.FRONT) && camera.hasCameraWithLensFacing(CameraX.LensFacing.BACK)) {
      flipButton.setVisibility(View.VISIBLE);
      flipButton.setOnClickListener(v ->  {
        camera.toggleCamera();
        TextSecurePreferences.setDirectCaptureCameraId(getContext(), CameraXUtil.toCameraDirectionInt(camera.getCameraLensFacing()));

        Animation animation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(200);
        animation.setInterpolator(new DecelerateInterpolator());
        flipButton.startAnimation(animation);
      });

      GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
          flipButton.performClick();
          return true;
        }
      });

      camera.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));


    } else {
      flipButton.setVisibility(View.GONE);
    }

    galleryButton.setOnClickListener(v -> controller.onGalleryClicked());
    countButton.setOnClickListener(v -> controller.onCameraCountButtonClicked());

    viewModel.onCameraControlsInitialized();
  }

  private void onCaptureClicked() {
    Stopwatch stopwatch = new Stopwatch("Capture");

    camera.takePicture(new ImageCapture.OnImageCapturedListener() {
      @Override
      public void onCaptureSuccess(ImageProxy image, int rotationDegrees) {
        SimpleTask.run(CameraXFragment.this.getLifecycle(), () -> {
          stopwatch.split("captured");
          try {
            return CameraXUtil.toJpeg(image, rotationDegrees, camera.getCameraLensFacing() == CameraX.LensFacing.FRONT);
          } catch (IOException e) {
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
      public void onError(ImageCapture.UseCaseError useCaseError, String message, @Nullable Throwable cause) {
        controller.onCameraError();
      }
    });
  }
}
