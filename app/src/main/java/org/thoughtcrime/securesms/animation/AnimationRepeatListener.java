package org.thoughtcrime.securesms.animation;

import android.animation.Animator;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

public final class AnimationRepeatListener implements Animator.AnimatorListener {

  private final Consumer<Animator> animationConsumer;

  public AnimationRepeatListener(@NonNull Consumer<Animator> animationConsumer) {
    this.animationConsumer = animationConsumer;
  }

  @Override
  public final void onAnimationStart(Animator animation) {
  }

  @Override
  public final void onAnimationEnd(Animator animation) {
  }

  @Override
  public final void onAnimationCancel(Animator animation) {
  }

  @Override
  public final void onAnimationRepeat(Animator animation) {
    this.animationConsumer.accept(animation);
  }
}
