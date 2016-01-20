package org.privatechats.securesms.components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import org.privatechats.securesms.R;

public class BubbleDrawableBuilder {
  private int color;
  private int shadowColor;
  private boolean hasShadow = true;
  private boolean[] corners = new boolean[]{true,true,true,true};

  protected BubbleDrawableBuilder() { }

  public BubbleDrawableBuilder setColor(int color) {
    this.color = color;
    return this;
  }

  public BubbleDrawableBuilder setShadowColor(int shadowColor) {
    this.shadowColor = shadowColor;
    return this;
  }

  public BubbleDrawableBuilder setHasShadow(boolean hasShadow) {
    this.hasShadow = hasShadow;
    return this;
  }

  public BubbleDrawableBuilder setCorners(boolean[] corners) {
    this.corners = corners;
    return this;
  }

  public Drawable create(Context context) {
    final GradientDrawable bubble = new GradientDrawable();
    final int              radius = context.getResources().getDimensionPixelSize(R.dimen.message_bubble_corner_radius);
    final float[]          radii  = cornerBooleansToRadii(corners, radius);

    bubble.setColor(color);
    bubble.setCornerRadii(radii);

    if (!hasShadow) {
      return bubble;
    } else {
      final GradientDrawable shadow   = new GradientDrawable();
      final int              distance = context.getResources().getDimensionPixelSize(R.dimen.message_bubble_shadow_distance);

      shadow.setColor(shadowColor);
      shadow.setCornerRadii(radii);

      final LayerDrawable layers = new LayerDrawable(new Drawable[]{shadow, bubble});
      layers.setLayerInset(1, 0, 0, 0, distance);
      return layers;
    }
  }

  private float[] cornerBooleansToRadii(boolean[] corners, int radius) {
    if (corners == null || corners.length != 4) {
      throw new AssertionError("there are four corners in a rectangle, silly");
    }

    float[] radii = new float[8];
    int     i     = 0;
    for (boolean corner : corners) {
      radii[i] = radii[i+1] = corner ? radius : 0;
      i += 2;
    }

    return radii;
  }

}
