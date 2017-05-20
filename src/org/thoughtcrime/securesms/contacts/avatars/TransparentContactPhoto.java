package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;

import com.makeramen.roundedimageview.RoundedDrawable;

import org.thoughtcrime.securesms.R;

public class TransparentContactPhoto implements ContactPhoto {

  TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(Context context, @ColorInt int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, @ColorInt int color, boolean inverted) {
    return RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));
  }

  @Override
  public Drawable asCallCard(Context context) {
    return ContextCompat.getDrawable(context, R.drawable.ic_contact_picture_large);
  }
}
