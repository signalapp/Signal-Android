package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
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

/**
 * Fallback resource based contact photo with a 20dp icon
 */
public final class FallbackPhoto20dp implements FallbackContactPhoto {

  @DrawableRes private final int drawable20dp;

  public FallbackPhoto20dp(@DrawableRes int drawable20dp) {
    this.drawable20dp = drawable20dp;
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color) {
    return buildDrawable(context, color);
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return buildDrawable(context, color);
  }

  @Override
  public Drawable asSmallDrawable(@NonNull Context context, @NonNull AvatarColor color, boolean inverted) {
    return buildDrawable(context, color);
  }

  @Override
  public Drawable asCallCard(@NonNull Context context) {
    throw new UnsupportedOperationException();
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context, @NonNull AvatarColor color) {
    Drawable      background      = DrawableCompat.wrap(Objects.requireNonNull(AppCompatResources.getDrawable(context, R.drawable.circle_tintable))).mutate();
    Drawable      foreground      = Objects.requireNonNull(AppCompatResources.getDrawable(context, drawable20dp));
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground});
    int           foregroundInset = ViewUtil.dpToPx(2);

    DrawableCompat.setTint(background, color.colorInt());
    DrawableCompat.setTint(foreground, Avatars.getForegroundColor(color).getColorInt());

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
