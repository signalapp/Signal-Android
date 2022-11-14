package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import java.util.Objects;

public final class ContextUtil {
  private ContextUtil() {}

  public static @NonNull Drawable requireDrawable(@NonNull Context context, @DrawableRes int drawable) {
    return Objects.requireNonNull(AppCompatResources.getDrawable(context, drawable));
  }

  /**
   * Implementation "borrowed" from com.airbnb.lottie.utils.Utils#getAnimationScale(android.content.Context)
   */
  public static float getAnimationScale(Context context) {
    return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f);
  }
}
