package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.makeramen.roundedimageview.RoundedDrawable;

public class BitmapContactPhoto implements ContactPhoto {

  private final Bitmap bitmap;

  BitmapContactPhoto(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return RoundedDrawable.fromBitmap(bitmap)
                          .setScaleType(ImageView.ScaleType.CENTER_CROP)
                          .setOval(true);
  }

  @Override
  public Drawable asCallCard(Context context) {
    return new BitmapDrawable(context.getResources(), bitmap);
  }

  @Override
  public boolean isGenerated() {
    return false;
  }

  @Override
  public boolean isResource() {
    return false;
  }
}
