package org.thoughtcrime.securesms.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
      setSpacerHeight(view, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top);
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
      setSpacerHeight(view, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
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

  @SuppressWarnings("deprecation")
  public void showAndHideWithSystemUI(@NonNull Window window, @NonNull View... views) {
    if (Build.VERSION.SDK_INT >= 30) {
      ViewCompat.setOnApplyWindowInsetsListener(window.getDecorView(), (view, insets) -> {
        animateWithBars(!areBarsVisible(insets), views);
        return ViewCompat.onApplyWindowInsets(view, insets);
      });
    } else {
      window.getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
        animateWithBars((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0, views);
      });
    }
  }

  private static void animateWithBars(boolean hide, @NonNull View... views) {
    for (View target : views) {
      if (target == null) {
        continue;
      }

      target.animate()
            .alpha(hide ? 0 : 1)
            .withStartAction(() -> {
              if (!hide) {
                target.setVisibility(View.VISIBLE);
              }
            })
            .withEndAction(() -> {
              if (hide) {
                target.setVisibility(View.INVISIBLE);
              }
            })
            .start();
    }
  }

  public void toggleUiVisibility() {
    if (isSystemUiVisible()) {
      hideSystemUI();
    } else {
      showSystemUI();
    }
  }

  @SuppressWarnings("deprecation")
  public boolean isSystemUiVisible() {
    if (Build.VERSION.SDK_INT >= 30) {
      return areBarsVisible(ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView()));
    } else {
      return (activity.getWindow().getDecorView().getSystemUiVisibility() & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
    }
  }

  /**
   * Whether the bars that {@link #showSystemUI()} / {@link #hideSystemUI()} control are currently on screen.
   * <p>
   * Only usable on API 30+. Below that, {@link WindowInsetsCompat#isVisible(int)} is emulated off of the inset
   * sizes, and because we're edge-to-edge (and therefore laid out with {@code SYSTEM_UI_FLAG_LAYOUT_STABLE})
   * those stay at their full height while the bars are hidden, making everything look permanently visible.
   * The legacy visibility flags are the reliable signal there.
   * <p>
   * Checks the two bars individually rather than {@link WindowInsetsCompat.Type#systemBars()}, which also
   * covers the caption bar: {@code isVisible} requires every requested type to be visible, and a phone window
   * has no caption bar source, so the aggregate answer is always "hidden".
   */
  private static boolean areBarsVisible(@Nullable WindowInsetsCompat insets) {
    if (insets == null) {
      return true;
    }

    return insets.isVisible(WindowInsetsCompat.Type.statusBars()) || insets.isVisible(WindowInsetsCompat.Type.navigationBars());
  }

  public void hideSystemUI() {
    hideSystemUI(activity.getWindow());
  }

  public static void hideSystemUI(@NonNull Window window) {
    WindowInsetsControllerCompat controller = controller(window);
    controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    controller.hide(WindowInsetsCompat.Type.systemBars());
  }

  public void showSystemUI() {
    showSystemUI(activity.getWindow());
  }

  public static void showSystemUI(@NonNull Window window) {
    controller(window).show(WindowInsetsCompat.Type.systemBars());
  }

  private static @NonNull WindowInsetsControllerCompat controller(@NonNull Window window) {
    return new WindowInsetsControllerCompat(window, window.getDecorView());
  }
}
