package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Objects;

/**
 * Fallback resource based contact photo with a 20dp icon
 */
public final class FallbackPhoto20dp implements FallbackContactPhoto {

  @DrawableRes private final int drawable20dp;

  public FallbackPhoto20dp(@DrawableRes int drawable20dp) {
    this.drawable20dp = drawable20dp;
  }

  @Override
  public Drawable asDrawable(Context context, int color) {
    return buildDrawable(context, color);
  }

  @Override
  public Drawable asDrawable(Context context, int color, boolean inverted) {
    return buildDrawable(context, color);
  }

  @Override
  public Drawable asSmallDrawable(Context context, int color, boolean inverted) {
    return buildDrawable(context, color);
  }

  @Override
  public Drawable asCallCard(Context context) {
    throw new UnsupportedOperationException();
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context, int color) {
    Drawable      background      = DrawableCompat.wrap(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.circle_tintable))).mutate();
    Drawable      foreground      = AppCompatResources.getDrawable(context, drawable20dp);
    Drawable      gradient        = ThemeUtil.getThemedDrawable(context, R.attr.resource_placeholder_gradient);
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground, gradient});
    int           foregroundInset = ViewUtil.dpToPx(2);

    DrawableCompat.setTint(background, color);

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
