package org.thoughtcrime.securesms.animation;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;

/**
 * Based on https://developer.android.com/training/animation/screen-slide#depth-page
 */
public final class DepthPageTransformer implements ViewPager.PageTransformer {
  private static final float MIN_SCALE = 0.75f;

  public void transformPage(@NonNull View view, float position) {
    final int pageWidth = view.getWidth();

    if (position < -1f) {
      view.setAlpha(0f);

    } else if (position <= 0f) {
      view.setAlpha(1f);
      view.setTranslationX(0f);
      view.setScaleX(1f);
      view.setScaleY(1f);

    } else if (position <= 1f) {
      view.setAlpha(1f - position);

      view.setTranslationX(pageWidth * -position);

      final float scaleFactor = MIN_SCALE + (1f - MIN_SCALE) * (1f - Math.abs(position));

      view.setScaleX(scaleFactor);
      view.setScaleY(scaleFactor);

    } else {
      view.setAlpha(0f);
    }
  }
}
