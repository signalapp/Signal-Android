package org.session.libsession.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.amulyakhare.textdrawable.TextDrawable;
import com.makeramen.roundedimageview.RoundedDrawable;


import org.session.libsession.R;
import org.session.libsession.utilities.ThemeUtil;

public class ResourceContactPhoto implements FallbackContactPhoto {

  private final int resourceId;

  public ResourceContactPhoto(@DrawableRes int resourceId) {
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

    Drawable gradient = context.getResources().getDrawable(ThemeUtil.isDarkTheme(context) ? R.drawable.avatar_gradient_dark
                                                                                          : R.drawable.avatar_gradient_light);

    return new ExpandingLayerDrawable(new Drawable[] {background, foreground, gradient});
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
