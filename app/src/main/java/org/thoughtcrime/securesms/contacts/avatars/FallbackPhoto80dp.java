package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.Avatars;
import org.thoughtcrime.securesms.conversation.colors.AvatarColor;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.Objects;

public final class FallbackPhoto80dp implements FallbackContactPhoto {

  @DrawableRes private final int         drawable80dp;
               private final AvatarColor color;

  public FallbackPhoto80dp(@DrawableRes int drawable80dp, @NonNull AvatarColor color) {
    this.drawable80dp = drawable80dp;
    this.color        = color;
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color) {
    return buildDrawable(context);
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return buildDrawable(context);
  }

  @Override
  public Drawable asSmallDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Drawable asCallCard(@NonNull Context context) {
    Drawable      background      = new ColorDrawable(color.colorInt());
    Drawable      foreground      = Objects.requireNonNull(AppCompatResources.getDrawable(context, drawable80dp));
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground});
    int           foregroundInset = ViewUtil.dpToPx(24);

    DrawableCompat.setTint(foreground, Avatars.getForegroundColor(color).getColorInt());
    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context) {
    Drawable      background      = DrawableCompat.wrap(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.circle_tintable))).mutate();
    Drawable      foreground      = Objects.requireNonNull(AppCompatResources.getDrawable(context, drawable80dp));
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground});
    int           foregroundInset = ViewUtil.dpToPx(24);

    DrawableCompat.setTint(background, color.colorInt());
    DrawableCompat.setTint(foreground, Avatars.getForegroundColor(color).getColorInt());

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
