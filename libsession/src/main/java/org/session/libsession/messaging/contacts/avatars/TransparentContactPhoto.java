package org.session.libsession.messaging.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

import org.session.libsession.R;

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
