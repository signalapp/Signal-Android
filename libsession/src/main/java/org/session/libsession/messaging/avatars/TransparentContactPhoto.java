package org.session.libsession.messaging.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.makeramen.roundedimageview.RoundedDrawable;

public class TransparentContactPhoto implements FallbackContactPhoto {

  public TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));
  }

}
