package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;

public interface ContactPhoto {

  Drawable asDrawable(Context context, @ColorInt int color);
  Drawable asDrawable(Context context, @ColorInt int color, boolean inverted);
  Drawable asCallCard(Context context);

}
