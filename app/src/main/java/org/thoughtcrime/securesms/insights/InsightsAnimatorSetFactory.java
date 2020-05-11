package org.thoughtcrime.securesms.insights;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

final class InsightsAnimatorSetFactory {
  private static final int   PROGRESS_ANIMATION_DURATION       = 800;
  private static final int   DETAILS_ANIMATION_DURATION        = 200;
  private static final int   PERCENT_SECURE_ANIMATION_DURATION = 400;
  private static final int   LOTTIE_ANIMATION_DURATION         = 1500;
  private static final int   ANIMATION_START_DELAY             = PROGRESS_ANIMATION_DURATION - DETAILS_ANIMATION_DURATION;
  private static final float PERCENT_SECURE_MAX_SCALE          = 1.3f;

  private InsightsAnimatorSetFactory() {
  }

  static AnimatorSet create(int insecurePercent,
                            @Nullable final UpdateListener progressUpdateListener,
                            @Nullable final UpdateListener detailsUpdateListener,
                            @Nullable final UpdateListener percentSecureListener,
                            @Nullable final UpdateListener lottieListener)
  {
    final int             securePercent = 100 - insecurePercent;
    final AnimatorSet     animatorSet   = new AnimatorSet();
    final ValueAnimator[] animators     = Stream.of(createProgressAnimator(securePercent, progressUpdateListener),
                                                    createDetailsAnimator(detailsUpdateListener),
                                                    createPercentSecureAnimator(percentSecureListener),
                                                    createLottieAnimator(lottieListener))
                                                .filter(a -> a != null)
                                                .toArray(ValueAnimator[]::new);

    animatorSet.setInterpolator(new DecelerateInterpolator());
    animatorSet.playTogether(animators);

    return animatorSet;
  }

  private static @Nullable Animator createProgressAnimator(int securePercent, @Nullable UpdateListener updateListener) {
    if (updateListener == null) return null;

    final ValueAnimator progressAnimator = ValueAnimator.ofFloat(0,  securePercent / 100f);

    progressAnimator.setDuration(PROGRESS_ANIMATION_DURATION);
    progressAnimator.addUpdateListener(animation -> updateListener.onUpdate((float) animation.getAnimatedValue()));

    return progressAnimator;
  }

  private static @Nullable Animator createDetailsAnimator(@Nullable UpdateListener updateListener) {
    if (updateListener == null) return null;

    final ValueAnimator detailsAnimator = ValueAnimator.ofFloat(0, 1f);

    detailsAnimator.setDuration(DETAILS_ANIMATION_DURATION);
    detailsAnimator.setStartDelay(ANIMATION_START_DELAY);
    detailsAnimator.addUpdateListener(animation -> updateListener.onUpdate((float) animation.getAnimatedValue()));

    return detailsAnimator;
  }

  private static @Nullable Animator createPercentSecureAnimator(@Nullable UpdateListener updateListener) {
    if (updateListener == null) return null;

    final ValueAnimator percentSecureAnimator = ValueAnimator.ofFloat(1f, PERCENT_SECURE_MAX_SCALE, 1f);

    percentSecureAnimator.setStartDelay(ANIMATION_START_DELAY);
    percentSecureAnimator.setDuration(PERCENT_SECURE_ANIMATION_DURATION);
    percentSecureAnimator.addUpdateListener(animation -> updateListener.onUpdate((float) animation.getAnimatedValue()));

    return percentSecureAnimator;
  }

  private static @Nullable Animator createLottieAnimator(@Nullable UpdateListener updateListener) {
    if (updateListener == null) return null;

    final ValueAnimator lottieAnimator = ValueAnimator.ofFloat(0, 1f);

    lottieAnimator.setStartDelay(ANIMATION_START_DELAY);
    lottieAnimator.setDuration(LOTTIE_ANIMATION_DURATION);
    lottieAnimator.addUpdateListener(animation -> updateListener.onUpdate((float) animation.getAnimatedValue()));

    return lottieAnimator;
  }

  interface UpdateListener {
    void onUpdate(float value);
  }
}
