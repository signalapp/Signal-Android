package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.Objects;

public final class ContextUtil {
  private ContextUtil() {}

  public static @NonNull Drawable requireDrawable(@NonNull Context context, @DrawableRes int drawable) {
    return Objects.requireNonNull(ContextCompat.getDrawable(context, drawable));
  }

  public static int getNightModeConfiguration(@NonNull Context context) {
    return context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
  }
}
