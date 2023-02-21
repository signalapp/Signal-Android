package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.signal.core.util.logging.Log;

/**
 * Encapsulates logic to properly show/hide system UI/chrome in a full screen setting. Also
 * handles adjusting to notched devices as long as you call {@link #configureToolbarLayout(View, View)}.
 */
public final class FullscreenHelper {

  @NonNull private final Activity activity;

  public FullscreenHelper(@NonNull Activity activity) {
   this(activity, false);
  }

  /**
   * @param activity              The activity we are controlling
   * @param suppressShowSystemUI  Suppresses the initial 'show system ui' call, which can cause the status and navbar to flash
   *                              during some animations.
   */
  public FullscreenHelper(@NonNull Activity activity, boolean suppressShowSystemUI) {
    this.activity = activity;

    if (Build.VERSION.SDK_INT >= 28) {
      activity.getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }

    if (!suppressShowSystemUI) {
      showSystemUI();
    }
  }

  public void configureToolbarLayout(@NonNull View spacer, @NonNull View toolbar) {
    ViewCompat.setOnApplyWindowInsetsListener(spacer, (view, insets) -> {
      setSpacerHeight(view, insets.getSystemWindowInsetTop());
      return insets;
    });

    ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, insets) -> {
      int[] padding = makePaddingValues(insets);
      toolbar.setPadding(padding[0], 0, padding[1], 0);
      return insets;
    });
  }

  public static void configureBottomBarLayout(@NonNull Activity activity, @NonNull View spacer, @NonNull View bottomBar) {
    ViewCompat.setOnApplyWindowInsetsListener(spacer, (view, insets) -> {
      setSpacerHeight(view, insets.getSystemWindowInsetBottom());
      return insets;
    });

    ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (view, insets) -> {
      int[] padding = makePaddingValues(insets);
      bottomBar.setPadding(padding[0], 0, padding[1], 0);
      return insets;
    });
  }

  private static void setSpacerHeight(@NonNull View spacer, int height) {
    ViewGroup.LayoutParams params = spacer.getLayoutParams();

    params.height = height;

    spacer.setLayoutParams(params);
    spacer.setVisibility(View.VISIBLE);
  }

  private static int[] makePaddingValues(WindowInsetsCompat insets) {
    Insets              tappable = insets.getTappableElementInsets();
    DisplayCutoutCompat cutout   = insets.getDisplayCutout();

    int leftPad  = cutout == null ? tappable.left
                                  : Math.max(tappable.left, cutout.getSafeInsetLeft());
    int rightPad = cutout == null ? tappable.right
                                  : Math.max(tappable.right, cutout.getSafeInsetRight());

    return new int[]{leftPad, rightPad};
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
    if (isSystemUiVisible()) {
      showSystemUI();
    } else {
      hideSystemUI();
    }
  }

  public boolean isSystemUiVisible() {
    int systemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
    return (systemUiVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
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
    showSystemUI(activity.getWindow());
  }

  public static void showSystemUI(@NonNull Window window) {
    window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE          |
                                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }
}
