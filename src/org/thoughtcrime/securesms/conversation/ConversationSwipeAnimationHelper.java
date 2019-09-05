package org.thoughtcrime.securesms.conversation;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.util.Util;

final class ConversationSwipeAnimationHelper {

  public static final float PROGRESS_TRIGGER_POINT = 0.375f;

  private static final float PROGRESS_SCALE_FACTOR          = 2.0f;
  private static final float SCALED_PROGRESS_TRIGGER_POINT  = PROGRESS_TRIGGER_POINT * PROGRESS_SCALE_FACTOR;
  private static final float REPLY_SCALE_OVERSHOOT          = 1.8f;
  private static final float REPLY_SCALE_MAX                = 1.2f;
  private static final float REPLY_SCALE_MIN                = 1f;
  private static final long  REPLY_SCALE_OVERSHOOT_DURATION = 200;

  private static final Interpolator BUBBLE_INTERPOLATOR           = new ClampingLinearInterpolator(0f, dpToPx(48));
  private static final Interpolator REPLY_ALPHA_INTERPOLATOR      = new ClampingLinearInterpolator(0f, 1f, 1f / SCALED_PROGRESS_TRIGGER_POINT);
  private static final Interpolator REPLY_TRANSITION_INTERPOLATOR = new ClampingLinearInterpolator(0f, dpToPx(10));
  private static final Interpolator AVATAR_INTERPOLATOR           = new ClampingLinearInterpolator(0f, dpToPx(8));
  private static final Interpolator REPLY_SCALE_INTERPOLATOR      = new ClampingLinearInterpolator(REPLY_SCALE_MIN, REPLY_SCALE_MAX);

  private ConversationSwipeAnimationHelper() {
  }

  public static void update(@NonNull ConversationItem conversationItem, float progress, float sign) {
    float scaledProgress = Math.min(1f, progress * PROGRESS_SCALE_FACTOR);
    updateBodyBubbleTransition(conversationItem.bodyBubble, scaledProgress, sign);
    updateReplyIconTransition(conversationItem.reply, scaledProgress, sign);
    updateContactPhotoHolderTransition(conversationItem.contactPhotoHolder, scaledProgress, sign);
  }

  public static void trigger(@NonNull ConversationItem conversationItem) {
    triggerReplyIcon(conversationItem.reply);
  }

  private static void updateBodyBubbleTransition(@NonNull View bodyBubble, float progress, float sign) {
    bodyBubble.setTranslationX(BUBBLE_INTERPOLATOR.getInterpolation(progress) * sign);
  }

  private static void updateReplyIconTransition(@NonNull View replyIcon, float progress, float sign) {
    if (progress > 0.05f) {
      replyIcon.setAlpha(REPLY_ALPHA_INTERPOLATOR.getInterpolation(progress));
    } else replyIcon.setAlpha(0f);

    replyIcon.setTranslationX(REPLY_TRANSITION_INTERPOLATOR.getInterpolation(progress) * sign);

    if (progress < SCALED_PROGRESS_TRIGGER_POINT) {
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
