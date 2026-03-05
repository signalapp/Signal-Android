package org.signal.core.util;

import android.content.res.Resources;

import androidx.annotation.Dimension;
import androidx.annotation.Px;

/**
 * Core utility for converting different dimensional values.
 */
public enum DimensionUnit {
  PIXELS {
    @Override
    @Px
    public float toPixels(@Px float pixels) {
      return pixels;
    }

    @Override
    @Dimension(unit = Dimension.DP)
    public float toDp(@Px float pixels) {
      return pixels / Resources.getSystem().getDisplayMetrics().density;
    }

    @Override
    @Dimension(unit = Dimension.SP)
    public float toSp(@Px float pixels) {
      return pixels / Resources.getSystem().getDisplayMetrics().scaledDensity;
    }
  },
  DP {
    @Override
    @Px
    public float toPixels(@Dimension(unit = Dimension.DP) float dp) {
      return dp * Resources.getSystem().getDisplayMetrics().density;
    }

    @Override
    @Dimension(unit = Dimension.DP)
    public float toDp(@Dimension(unit = Dimension.DP) float dp) {
      return dp;
    }

    @Override
    @Dimension(unit = Dimension.SP)
    public float toSp(@Dimension(unit = Dimension.DP) float dp) {
      return PIXELS.toSp(toPixels(dp));
    }
  },
  SP {
    @Override
    @Px
    public float toPixels(@Dimension(unit = Dimension.SP) float sp) {
      return sp * Resources.getSystem().getDisplayMetrics().scaledDensity;
    }

    @Override
    @Dimension(unit = Dimension.DP)
    public float toDp(@Dimension(unit = Dimension.SP) float sp) {
      return PIXELS.toDp(toPixels(sp));
    }

    @Override
    @Dimension(unit = Dimension.SP)
    public float toSp(@Dimension(unit = Dimension.SP) float sp) {
      return sp;
    }
  };

  public abstract float toPixels(float value);
  public abstract float toDp(float value);
  public abstract float toSp(float value);
}
