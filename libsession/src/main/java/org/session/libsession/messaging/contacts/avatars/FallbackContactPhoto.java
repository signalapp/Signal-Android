package org.session.libsession.messaging.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

public interface FallbackContactPhoto {

  public Drawable asDrawable(Context context, int color);
  public Drawable asDrawable(Context context, int color, boolean inverted);

}
