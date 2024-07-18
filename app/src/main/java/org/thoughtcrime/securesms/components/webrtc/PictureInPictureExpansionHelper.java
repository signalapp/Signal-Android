package org.thoughtcrime.securesms.components.webrtc;

import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.transition.AutoTransition;
import androidx.transition.Transition;
import androidx.transition.TransitionListenerAdapter;
import androidx.transition.TransitionManager;

import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Helps manage the expansion and shrinking of the in-app pip.
 */
@MainThread
final class PictureInPictureExpansionHelper {

  private static final int PIP_RESIZE_DURATION_MS = 300;
  private static final int EXPANDED_PIP_WIDTH_DP  = 170;
  private static final int EXPANDED_PIP_HEIGHT_DP = 300;

  public static final int NORMAL_PIP_WIDTH_DP = 90;
  public static final int NORMAL_PIP_HEIGHT_DP = 160;

  public static final int MINI_PIP_WIDTH_DP = 40;
  public static final int MINI_PIP_HEIGHT_DP = 72;

  private final View      selfPip;
  private final ViewGroup parent;

  private State state = State.IS_SHRUNKEN;
  private Point defaultDimensions;
  private Point expandedDimensions;

  public PictureInPictureExpansionHelper(@NonNull View selfPip) {
    this.selfPip            = selfPip;
    this.parent             = (ViewGroup) selfPip.getParent();
    this.defaultDimensions  = new Point(selfPip.getLayoutParams().width, selfPip.getLayoutParams().height);
    this.expandedDimensions = new Point(ViewUtil.dpToPx(EXPANDED_PIP_WIDTH_DP), ViewUtil.dpToPx(EXPANDED_PIP_HEIGHT_DP));
  }

  public boolean isExpandedOrExpanding() {
    return state == State.IS_EXPANDED || state == State.IS_EXPANDING;
  }

  public boolean isShrunkenOrShrinking() {
    return state == State.IS_SHRUNKEN || state == State.IS_SHRINKING;
  }

  public boolean isMiniSize() {
    return defaultDimensions.x < ViewUtil.dpToPx(NORMAL_PIP_WIDTH_DP);
  }

  public void startDefaultSizeTransition(@NonNull Point dimensions, @NonNull Callback callback) {
    if (defaultDimensions.equals(dimensions)) {
      return;
    }

    defaultDimensions = dimensions;

    int x = (dimensions.x > dimensions.y) ? EXPANDED_PIP_HEIGHT_DP : EXPANDED_PIP_WIDTH_DP;
    int y = (dimensions.x > dimensions.y) ? EXPANDED_PIP_WIDTH_DP  : EXPANDED_PIP_HEIGHT_DP;

    expandedDimensions = new Point(ViewUtil.dpToPx(x), ViewUtil.dpToPx(y));

    if (isExpandedOrExpanding()) {
      return;
    }

    beginResizeSelfPipTransition(defaultDimensions, callback);
  }

  public void beginExpandTransition() {
    if (isExpandedOrExpanding()) {
      return;
    }

    beginResizeSelfPipTransition(expandedDimensions, new Callback() {
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

  public void beginShrinkTransition() {
    if (isShrunkenOrShrinking()) {
      return;
    }

    beginResizeSelfPipTransition(defaultDimensions, new Callback() {
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

  private void beginResizeSelfPipTransition(@NonNull Point dimension, @NonNull Callback callback) {
    TransitionManager.endTransitions(parent);

    Transition transition = new AutoTransition().setDuration(PIP_RESIZE_DURATION_MS);
    transition.addListener(new TransitionListenerAdapter() {
      @Override
      public void onTransitionStart(@NonNull Transition transition) {
        callback.onAnimationWillStart();
      }

      @Override
      public void onTransitionEnd(@NonNull Transition transition) {
        callback.onAnimationHasFinished();
      }
    });

    TransitionManager.beginDelayedTransition(parent, transition);

    ViewGroup.LayoutParams params = selfPip.getLayoutParams();

    params.width  = dimension.x;
    params.height = dimension.y;

    selfPip.setLayoutParams(params);
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
