package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.support.annotation.DrawableRes;
import android.support.v4.graphics.ColorUtils;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.makeramen.roundedimageview.RoundedDrawable;

public class ResourceContactPhoto implements ContactPhoto {

  private final int resourceId;

  ResourceContactPhoto(@DrawableRes int resourceId) {
    this.resourceId = resourceId;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return asDrawable(context, color, false);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    Drawable        background = TextDrawable.builder().buildRound(" ", inverted ? Color.WHITE : color);
    RoundedDrawable foreground = (RoundedDrawable) RoundedDrawable.fromDrawable(context.getResources().getDrawable(resourceId));

    foreground.setScaleType(ImageView.ScaleType.CENTER);

    if (inverted) {
      foreground.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    return new ExpandingLayerDrawable(new Drawable[] {background, foreground});
  }

  private static class ExpandingLayerDrawable extends LayerDrawable {
    public ExpandingLayerDrawable(Drawable[] layers) {
      super(layers);
    }

    @Override
    public int getIntrinsicWidth() {
      return -1;
    }

    @Override
    public int getIntrinsicHeight() {
      return -1;
    }
  }

}
