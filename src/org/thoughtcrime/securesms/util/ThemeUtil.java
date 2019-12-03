package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import androidx.annotation.AttrRes;
import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;

import org.thoughtcrime.securesms.R;

public class ThemeUtil {

  public static boolean isDarkTheme(@NonNull Context context) {
    return getAttribute(context, R.attr.theme_type, "light").equals("dark");
  }

  public static boolean getThemedBoolean(@NonNull Context context, @AttrRes int attr) {
    TypedValue      typedValue = new TypedValue();
    Resources.Theme theme      = context.getTheme();

    if (theme.resolveAttribute(attr, typedValue, true)) {
      return typedValue.data != 0;
    }

    return false;
  }

  public static int getThemedColor(@NonNull Context context, @AttrRes int attr) {
    TypedValue typedValue = new TypedValue();
    Resources.Theme theme = context.getTheme();

    if (theme.resolveAttribute(attr, typedValue, true)) {
      return typedValue.data;
    }
    return Color.RED;
  }

  public static @Nullable Drawable getThemedDrawable(@NonNull Context context, @AttrRes int attr) {
    TypedValue      typedValue = new TypedValue();
    Resources.Theme theme      = context.getTheme();

    if (theme.resolveAttribute(attr, typedValue, true)) {
      return ContextCompat.getDrawable(context, typedValue.resourceId);
    }

    return null;
  }

  public static LayoutInflater getThemedInflater(@NonNull Context context, @NonNull LayoutInflater inflater, @StyleRes int theme) {
    Context contextThemeWrapper = new ContextThemeWrapper(context, theme);
    return inflater.cloneInContext(contextThemeWrapper);
  }

  public static float getThemedDimen(@NonNull Context context, @AttrRes int attr) {
    TypedValue typedValue = new TypedValue();
    Resources.Theme theme = context.getTheme();

    if (theme.resolveAttribute(attr, typedValue, true)) {
      return typedValue.getDimension(context.getResources().getDisplayMetrics());
    }

    return 0;
  }

  private static String getAttribute(Context context, int attribute, String defaultValue) {
    TypedValue outValue = new TypedValue();

    if (context.getTheme().resolveAttribute(attribute, outValue, true)) {
      CharSequence charSequence = outValue.coerceToString();
      if (charSequence != null) {
        return charSequence.toString();
      }
    }

    return defaultValue;
  }
}
