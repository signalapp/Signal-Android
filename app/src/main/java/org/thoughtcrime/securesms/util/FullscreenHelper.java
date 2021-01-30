package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

/**
 * Encapsulates logic to properly show/hide system UI/chrome in a full screen setting. Also
 * handles adjusting to notched devices as long as you call {@link #configureToolbarSpacer(View)}.
 */
public final class FullscreenHelper {

  @NonNull private final Activity activity;

  public FullscreenHelper(@NonNull Activity activity) {
    this.activity = activity;

    if (Build.VERSION.SDK_INT >= 28) {
      activity.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }

    showSystemUI();
  }

  public void configureToolbarSpacer(@NonNull View spacer) {
    if (Build.VERSION.SDK_INT == 19) {
      setSpacerHeight(spacer, ViewUtil.getStatusBarHeight(spacer));
      return;
    }

    ViewCompat.setOnApplyWindowInsetsListener(spacer, (view, insets) -> {
      setSpacerHeight(view, insets.getSystemWindowInsetTop());
      return insets;
    });
  }

  private void setSpacerHeight(@NonNull View spacer, int height) {
    ViewGroup.LayoutParams params = spacer.getLayoutParams();

    params.height = height;

    spacer.setLayoutParams(params);
    spacer.setVisibility(View.VISIBLE);
  }

  public void showAndHideWithSystemUI(@NonNull Window window, @NonNull View... views) {
    window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
      boolean hide = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;

      for (View view : views) {
        view.animate()
            .alpha(hide ? 0 : 1)
            .withStartAction(() -> {
              if (!hide) {
                view.setVisibility(View.VISIBLE);
              }
            })
            .withEndAction(() -> {
              if (hide) {
                view.setVisibility(View.INVISIBLE);
              }
            })
            .start();
      }
    });
  }

  public void toggleUiVisibility() {
    int systemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
    if ((systemUiVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0) {
      showSystemUI();
    } else {
      hideSystemUI();
    }
  }

  public void hideSystemUI() {
    activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE              |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      |
                                                              View.SYSTEM_UI_FLAG_HIDE_NAVIGATION        |
                                                              View.SYSTEM_UI_FLAG_FULLSCREEN);
  }

  public void showSystemUI() {
    activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                              View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }
}
