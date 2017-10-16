package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.support.annotation.DrawableRes;
import android.support.v7.content.res.AppCompatResources;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.makeramen.roundedimageview.RoundedDrawable;

public class ResourceContactPhoto implements FallbackContactPhoto {

  private final int resourceId;
  private final int callCardResourceId;

  public ResourceContactPhoto(@DrawableRes int resourceId) {
    this(resourceId, resourceId);
  }

  public ResourceContactPhoto(@DrawableRes int resourceId, @DrawableRes int callCardResourceId) {
    this.resourceId         = resourceId;
    this.callCardResourceId = callCardResourceId;
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

  @Override
  public Drawable asCallCard(Context context) {
    return AppCompatResources.getDrawable(context, callCardResourceId);
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
