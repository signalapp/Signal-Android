package org.thoughtcrime.securesms.contacts.avatars;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
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
  public Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors) {
    return buildDrawable(context, chatColors);
  }

  @Override
  public Drawable asDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    return buildDrawable(context, chatColors);
  }

  @Override
  public Drawable asSmallDrawable(@NonNull Context context, @NonNull ChatColors chatColors, boolean inverted) {
    return buildDrawable(context, chatColors);
  }

  @Override
  public Drawable asCallCard(Context context) {
    throw new UnsupportedOperationException();
  }

  private @NonNull Drawable buildDrawable(@NonNull Context context, @NonNull ChatColors backgroundColor) {
    Drawable      background      = backgroundColor.asCircle();
    Drawable      foreground      = AppCompatResources.getDrawable(context, drawable20dp);
    Drawable      gradient        = AppCompatResources.getDrawable(context, R.drawable.avatar_gradient);
    LayerDrawable drawable        = new LayerDrawable(new Drawable[]{background, foreground, gradient});
    int           foregroundInset = ViewUtil.dpToPx(2);

    drawable.setLayerInset(1, foregroundInset, foregroundInset, foregroundInset, foregroundInset);

    return drawable;
  }
}
