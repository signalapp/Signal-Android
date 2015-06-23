package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.makeramen.roundedimageview.RoundedDrawable;

public class ResourceContactPhoto implements ContactPhoto {

  private final int resourceId;
  private final int color;

  ResourceContactPhoto(int resourceId, int color) {
    this.resourceId = resourceId;
    this.color      = color;
  }

  @Override
  public Drawable asDrawable(Context context) {
    Drawable        background = TextDrawable.builder().buildRound(" ", color);
    RoundedDrawable foreground = (RoundedDrawable) RoundedDrawable.fromDrawable(context.getResources().getDrawable(resourceId));
    foreground.setScaleType(ImageView.ScaleType.CENTER);

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
