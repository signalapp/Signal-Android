package org.thoughtcrime.securesms.conversation;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.Util;

final class ConversationSwipeAnimationHelper {

  static final float TRIGGER_DX = dpToPx(64);
  static final float MAX_DX     = dpToPx(96);

  private static final float REPLY_SCALE_OVERSHOOT          = 1.8f;
  private static final float REPLY_SCALE_MAX                = 1.2f;
  private static final float REPLY_SCALE_MIN                = 1f;
  private static final long  REPLY_SCALE_OVERSHOOT_DURATION = 200;

  private static final Interpolator BUBBLE_INTERPOLATOR           = new BubblePositionInterpolator(0f, TRIGGER_DX, MAX_DX);
  private static final Interpolator REPLY_ALPHA_INTERPOLATOR      = new ClampingLinearInterpolator(0f, 1f, 1f);
  private static final Interpolator REPLY_TRANSITION_INTERPOLATOR = new ClampingLinearInterpolator(0f, dpToPx(10));
  private static final Interpolator AVATAR_INTERPOLATOR           = new ClampingLinearInterpolator(0f, dpToPx(8));
  private static final Interpolator REPLY_SCALE_INTERPOLATOR      = new ClampingLinearInterpolator(REPLY_SCALE_MIN, REPLY_SCALE_MAX);

  private ConversationSwipeAnimationHelper() {
  }

  public static void update(@NonNull ConversationItem conversationItem, float dx, float sign) {
    float progress = dx / TRIGGER_DX;

    updateBodyBubbleTransition(conversationItem.bodyBubble, dx, sign);
    updateReactionsTransition(conversationItem.reactionsView, dx, sign);
    updateReplyIconTransition(conversationItem.reply, dx, progress, sign);
    updateContactPhotoHolderTransition(conversationItem.contactPhotoHolder, progress, sign);
    updateContactPhotoHolderTransition(conversationItem.badgeImageView, progress, sign);
  }

  public static void trigger(@NonNull ConversationItem conversationItem) {
    triggerReplyIcon(conversationItem.reply);
  }

  private static void updateBodyBubbleTransition(@NonNull View bodyBubble, float dx, float sign) {
    bodyBubble.setTranslationX(BUBBLE_INTERPOLATOR.getInterpolation(dx) * sign);
  }

  private static void updateReactionsTransition(@NonNull View reactionsContainer, float dx, float sign) {
    reactionsContainer.setTranslationX(BUBBLE_INTERPOLATOR.getInterpolation(dx) * sign);
  }

  private static void updateReplyIconTransition(@NonNull View replyIcon, float dx, float progress, float sign) {
    if (progress > 0.05f) {
      replyIcon.setAlpha(REPLY_ALPHA_INTERPOLATOR.getInterpolation(progress));
    } else replyIcon.setAlpha(0f);

    replyIcon.setTranslationX(REPLY_TRANSITION_INTERPOLATOR.getInterpolation(progress) * sign);

    if (dx < TRIGGER_DX) {
      float scale = REPLY_SCALE_INTERPOLATOR.getInterpolation(progress);
      replyIcon.setScaleX(scale);
      replyIcon.setScaleY(scale);
    }
  }

  private static void updateContactPhotoHolderTransition(@Nullable View contactPhotoHolder,
                                                         float progress,
                                                         float sign)
  {
    if (contactPhotoHolder == null) return;
    contactPhotoHolder.setTranslationX(AVATAR_INTERPOLATOR.getInterpolation(progress) * sign);
  }

  private static void triggerReplyIcon(@NonNull View replyIcon) {
    ValueAnimator animator = ValueAnimator.ofFloat(REPLY_SCALE_MAX, REPLY_SCALE_OVERSHOOT, REPLY_SCALE_MAX);
    animator.setDuration(REPLY_SCALE_OVERSHOOT_DURATION);
    animator.addUpdateListener(animation -> {
      replyIcon.setScaleX((float) animation.getAnimatedValue());
      replyIcon.setScaleY((float) animation.getAnimatedValue());
    });
    animator.start();
  }

  private static int dpToPx(int dp) {
    return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
  }

  private static final class BubblePositionInterpolator implements Interpolator {

    private final float start;
    private final float middle;
    private final float end;

    private BubblePositionInterpolator(float start, float middle, float end) {
      this.start  = start;
      this.middle = middle;
      this.end    = end;
    }

    @Override
    public float getInterpolation(float input) {
      if (input < start) {
        return start;
      } else if (input < middle) {
        return input;
      } else {
        float segmentLength     = end   - middle;
        float segmentTraveled   = input - middle;
        float segmentCompletion = segmentTraveled / segmentLength;
        float scaleDownFactor   = middle / (input * 2);
        float output            = middle + (segmentLength * segmentCompletion * scaleDownFactor);

        return Math.min(output, end);
      }
    }
  }

  private static final class ClampingLinearInterpolator implements Interpolator {

    private final float slope;
    private final float yIntercept;
    private final float max;
    private final float min;

    ClampingLinearInterpolator(float start, float end) {
      this(start, end, 1.0f);
    }

    ClampingLinearInterpolator(float start, float end, float scale) {
      slope      = (end - start) * scale;
      yIntercept = start;
      max        = Math.max(start, end);
      min        = Math.min(start, end);
    }

    @Override
    public float getInterpolation(float input) {
      return Util.clamp(slope * input + yIntercept, min, max);
    }
  }

}
