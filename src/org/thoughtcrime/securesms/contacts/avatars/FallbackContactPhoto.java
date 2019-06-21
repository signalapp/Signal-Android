package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

public interface FallbackContactPhoto {

  Drawable asDrawable(Context context, int color);
  Drawable asDrawable(Context context, int color, boolean inverted);
  Drawable asCallCard(Context context);

}
