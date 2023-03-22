package org.thoughtcrime.securesms.animation.transitions;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;

/**
 * Will only transition {@link android.widget.ImageView}s that contain a {@link androidx.core.graphics.drawable.RoundedBitmapDrawable}.
 */
public final class SquareToCircleImageViewTransition extends CircleSquareImageViewTransition {
  public SquareToCircleImageViewTransition(Context context, AttributeSet attrs) {
    super(true);
  }
}
