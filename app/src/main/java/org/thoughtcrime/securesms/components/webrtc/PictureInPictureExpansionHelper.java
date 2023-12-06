package org.thoughtcrime.securesms.components.webrtc;

import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.animation.ResizeAnimation;
import org.thoughtcrime.securesms.mediasend.SimpleAnimationListener;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Helps manage the expansion and shrinking of the in-app pip.
 */
@MainThread
final class PictureInPictureExpansionHelper {

  private static final int PIP_RESIZE_DURATION_MS = 300;
  private static final int EXPANDED_PIP_WIDTH_DP  = 170;
  private static final int EXPANDED_PIP_HEIGHT_DP = 300;

  private final View  selfPip;
  private final Point expandedDimensions;

  private State state = State.IS_SHRUNKEN;
  private Point defaultDimensions;

  public PictureInPictureExpansionHelper(@NonNull View selfPip) {
    this.selfPip            = selfPip;
    this.defaultDimensions  = new Point(selfPip.getLayoutParams().width, selfPip.getLayoutParams().height);
    this.expandedDimensions = new Point(ViewUtil.dpToPx(EXPANDED_PIP_WIDTH_DP), ViewUtil.dpToPx(EXPANDED_PIP_HEIGHT_DP));
  }

  public boolean isExpandedOrExpanding() {
    return state == State.IS_EXPANDED || state == State.IS_EXPANDING;
  }

  public boolean isShrunkenOrShrinking() {
    return state == State.IS_SHRUNKEN || state == State.IS_SHRINKING;
  }

  public void setDefaultSize(@NonNull Point dimensions, @NonNull Callback callback) {
    if (defaultDimensions.equals(dimensions)) {
      return;
    }

    defaultDimensions = dimensions;

    if (isExpandedOrExpanding()) {
      return;
    }

    ViewGroup.LayoutParams layoutParams = selfPip.getLayoutParams();
    if (layoutParams.width == defaultDimensions.x && layoutParams.height == defaultDimensions.y) {
      callback.onAnimationHasFinished();
      return;
    }

    resizeSelfPip(defaultDimensions, callback);
  }

  public void expand() {
    if (isExpandedOrExpanding()) {
      return;
    }

    resizeSelfPip(expandedDimensions, new Callback() {
      @Override
      public void onAnimationWillStart() {
        state = State.IS_EXPANDING;
      }

      @Override
      public void onAnimationHasFinished() {
        state = State.IS_EXPANDED;
      }
    });
  }

  public void shrink() {
    if (isShrunkenOrShrinking()) {
      return;
    }

    resizeSelfPip(defaultDimensions, new Callback() {
      @Override
      public void onAnimationWillStart() {
        state = State.IS_SHRINKING;
      }

      @Override
      public void onAnimationHasFinished() {
        state = State.IS_SHRUNKEN;
      }
    });
  }

  private void resizeSelfPip(@NonNull Point dimension, @NonNull Callback callback) {
    ResizeAnimation resizeAnimation = new ResizeAnimation(selfPip, dimension);
    resizeAnimation.setDuration(PIP_RESIZE_DURATION_MS);
    resizeAnimation.setAnimationListener(new SimpleAnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {
        callback.onAnimationWillStart();
      }

      @Override
      public void onAnimationEnd(Animation animation) {
        callback.onAnimationHasFinished();
      }
    });

    selfPip.clearAnimation();
    selfPip.startAnimation(resizeAnimation);
  }

  enum State {
    IS_EXPANDING,
    IS_EXPANDED,
    IS_SHRINKING,
    IS_SHRUNKEN
  }

  public interface Callback {
    /**
     * Called when an animation (shrink or expand) will begin. This happens before any animation
     * is executed.
     */
    default void onAnimationWillStart() {}

    /**
     * Called when the animation is complete. Useful for e.g. adjusting the pip's final location to
     * make sure it is respecting the screen space available.
     */
    default void onAnimationHasFinished() {}
  }
}
