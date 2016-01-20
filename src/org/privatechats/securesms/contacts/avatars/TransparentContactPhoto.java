package org.privatechats.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.makeramen.roundedimageview.RoundedDrawable;

import org.privatechats.securesms.R;

public class TransparentContactPhoto implements ContactPhoto {

  TransparentContactPhoto() {}

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return RoundedDrawable.fromDrawable(context.getResources().getDrawable(android.R.color.transparent));
  }

  @Override
  public Drawable asCallCard(Context context) {
    return context.getResources().getDrawable(R.drawable.ic_contact_picture);
  }
}
