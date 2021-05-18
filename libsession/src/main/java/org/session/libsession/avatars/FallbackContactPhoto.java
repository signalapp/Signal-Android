package org.session.libsession.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

public interface FallbackContactPhoto {

  public Drawable asDrawable(Context context, int color);
  public Drawable asDrawable(Context context, int color, boolean inverted);
}
