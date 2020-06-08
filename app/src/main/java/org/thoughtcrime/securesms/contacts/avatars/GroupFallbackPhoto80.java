package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Objects;

public final class GroupFallbackPhoto80 implements FallbackContactPhoto {
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
    throw new UnsupportedOperationException();
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context) {
    Drawable      background      = DrawableCompat.wrap(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.circle_tintable)));
    Drawable      foreground      = AppCompatResources.getDrawable(context, R.drawable.ic_group_80);
    Drawable      gradient        = ThemeUtil.getThemedDrawable(context, R.attr.resource_placeholder_gradient);
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground, gradient});
    int           foregroundInset = ViewUtil.dpToPx(24);

    DrawableCompat.setTint(background, MaterialColor.ULTRAMARINE.toAvatarColor(context));

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
