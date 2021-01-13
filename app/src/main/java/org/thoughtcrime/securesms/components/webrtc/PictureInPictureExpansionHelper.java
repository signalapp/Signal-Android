package org.thoughtcrime.securesms.components.webrtc;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * Helps manage the expansion and shrinking of the in-app pip.
 */
@MainThread
final class PictureInPictureExpansionHelper {

  private State state = State.IS_SHRUNKEN;

  public boolean isExpandedOrExpanding() {
    return state == State.IS_EXPANDED || state == State.IS_EXPANDING;
  }

  public boolean isShrunkenOrShrinking() {
    return state == State.IS_SHRUNKEN || state == State.IS_SHRINKING;
  }

  public void expand(@NonNull View toExpand, @NonNull Callback callback) {
    if (isExpandedOrExpanding()) {
      return;
    }

    performExpandAnimation(toExpand, new Callback() {
      @Override
      public void onAnimationWillStart() {
        state = State.IS_EXPANDING;
        callback.onAnimationWillStart();
      }

      @Override
      public void onPictureInPictureExpanded() {
        callback.onPictureInPictureExpanded();
      }

      @Override
      public void onPictureInPictureNotVisible() {
        callback.onPictureInPictureNotVisible();
      }

      @Override
      public void onAnimationHasFinished() {
        state = State.IS_EXPANDED;
        callback.onAnimationHasFinished();
      }
    });
  }

  public void shrink(@NonNull View toExpand, @NonNull Callback callback) {
    if (isShrunkenOrShrinking()) {
      return;
    }

    performShrinkAnimation(toExpand, new Callback() {
      @Override
      public void onAnimationWillStart() {
        state = State.IS_SHRINKING;
        callback.onAnimationWillStart();
      }

      @Override
      public void onPictureInPictureExpanded() {
        callback.onPictureInPictureExpanded();
      }

      @Override
      public void onPictureInPictureNotVisible() {
        callback.onPictureInPictureNotVisible();
      }

      @Override
      public void onAnimationHasFinished() {
        state = State.IS_SHRUNKEN;
        callback.onAnimationHasFinished();
      }
    });
  }

  private void performExpandAnimation(@NonNull View target, @NonNull Callback callback) {
    ViewGroup parent = (ViewGroup) target.getParent();

    float x      = target.getX();
    float y      = target.getY();
    float scaleX = parent.getMeasuredWidth() / (float) target.getMeasuredWidth();
    float scaleY = parent.getMeasuredHeight() / (float) target.getMeasuredHeight();
    float scale  = Math.max(scaleX, scaleY);

    callback.onAnimationWillStart();

    target.animate()
          .setDuration(200)
          .x((parent.getMeasuredWidth() - target.getMeasuredWidth()) / 2f)
          .y((parent.getMeasuredHeight() - target.getMeasuredHeight()) / 2f)
          .scaleX(scale)
          .scaleY(scale)
          .withEndAction(() -> {
            callback.onPictureInPictureExpanded();
            target.animate()
                  .setDuration(100)
                  .alpha(0f)
                  .withEndAction(() -> {
                    callback.onPictureInPictureNotVisible();

                    target.setX(x);
                    target.setY(y);
                    target.setScaleX(0f);
                    target.setScaleY(0f);
                    target.setAlpha(1f);

                    target.animate()
                          .setDuration(200)
                          .scaleX(1f)
                          .scaleY(1f)
                          .withEndAction(callback::onAnimationHasFinished);
                  });
          });
  }

  private void performShrinkAnimation(@NonNull View target, @NonNull Callback callback) {
    ViewGroup parent = (ViewGroup) target.getParent();

    float x      = target.getX();
    float y      = target.getY();
    float scaleX = parent.getMeasuredWidth() / (float) target.getMeasuredWidth();
    float scaleY = parent.getMeasuredHeight() / (float) target.getMeasuredHeight();
    float scale  = Math.max(scaleX, scaleY);

    callback.onAnimationWillStart();

    target.animate()
          .setDuration(200)
          .scaleX(0f)
          .scaleY(0f)
          .withEndAction(() -> {
            target.setX((parent.getMeasuredWidth() - target.getMeasuredWidth()) / 2f);
            target.setY((parent.getMeasuredHeight() - target.getMeasuredHeight()) / 2f);
            target.setAlpha(0f);
            target.setScaleX(scale);
            target.setScaleY(scale);

            callback.onPictureInPictureNotVisible();

            target.animate()
                  .setDuration(100)
                  .alpha(1f)
                  .withEndAction(() -> {
                    callback.onPictureInPictureExpanded();

                    target.animate()
                          .scaleX(1f)
                          .scaleY(1f)
                          .x(x)
                          .y(y)
                          .withEndAction(callback::onAnimationHasFinished);
                  });
            });
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
    void onAnimationWillStart();

    /**
     * Called when the PiP is covering the whole screen. This is when any staging / teardown of the
     * large local renderer should occur.
     */
    void onPictureInPictureExpanded();

    /**
     * Called when the PiP is not visible on the screen anymore. This is when any staging / teardown
     * of the pip should occur.
     */
    void onPictureInPictureNotVisible();

    /**
     * Called when the animation is complete. Useful for e.g. adjusting the pip's final location to
     * make sure it is respecting the screen space available.
     */
    void onAnimationHasFinished();
  }
}
