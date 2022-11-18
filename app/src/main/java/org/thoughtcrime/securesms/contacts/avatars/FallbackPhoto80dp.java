package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Objects;

public final class FallbackPhoto80dp implements FallbackContactPhoto {

  @DrawableRes private final int drawable80dp;
               private final int backgroundColor;

  public FallbackPhoto80dp(@DrawableRes int drawable80dp, int backgroundColor) {
    this.drawable80dp    = drawable80dp;
    this.backgroundColor = backgroundColor;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return buildDrawable(context);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return buildDrawable(context);
  }

  @Override
  public Drawable asSmallDrawable(Context context, int color, boolean inverted) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Drawable asCallCard(Context context) {
    Drawable      background      = new ColorDrawable(backgroundColor);
    Drawable      foreground      = AppCompatResources.getDrawable(context, drawable80dp);
    int           transparent20   = ContextCompat.getColor(context, R.color.signal_transparent_20);
    Drawable      gradient        = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ Color.TRANSPARENT, transparent20 });
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground, gradient});
    int           foregroundInset = ViewUtil.dpToPx(24);

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context) {
    Drawable      background      = DrawableCompat.wrap(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.circle_tintable))).mutate();
    Drawable      foreground      = AppCompatResources.getDrawable(context, drawable80dp);
    Drawable      gradient        = AppCompatResources.getDrawable(context, R.drawable.avatar_gradient);
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground, gradient});
    int           foregroundInset = ViewUtil.dpToPx(24);

    DrawableCompat.setTint(background, backgroundColor);

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
