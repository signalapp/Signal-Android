package org.thoughtcrime.securesms.components.emoji;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.Callback;
import android.text.style.ImageSpan;

public class InvalidatingDrawableSpan extends ImageSpan {
  public InvalidatingDrawableSpan(Drawable drawable, Callback callback) {
    super(drawable, ALIGN_BOTTOM);
    drawable.setCallback(callback);
  }
}
