package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;

import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;

import org.thoughtcrime.securesms.R;

public class ThemeUtil {

  public static boolean isDarkTheme(@NonNull Context context) {
    return getAttribute(context, R.attr.theme_type, "light").equals("dark");
  }

  public static int getThemedResourceId(@NonNull Context context, @AttrRes int attr) {
    TypedValue      typedValue = new TypedValue();
    Resources.Theme theme      = context.getTheme();

    if (theme.resolveAttribute(attr, typedValue, true)) {
      return typedValue.resourceId;
    }

    return -1;
  }

  public static boolean getThemedBoolean(@NonNull Context context, @AttrRes int attr) {
    TypedValue      typedValue = new TypedValue();
    Resources.Theme theme      = context.getTheme();

    if (theme.resolveAttribute(attr, typedValue, true)) {
      return typedValue.data != 0;
    }

    return false;
  }

  public static @ColorInt int getThemedColor(@NonNull Context context, @AttrRes int attr) {
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
      return AppCompatResources.getDrawable(context, typedValue.resourceId);
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

  public static @ColorInt int getLightThemeNavigationBarColor(@NonNull Context context) {
    if (Build.VERSION.SDK_INT < 21) return context.getResources().getColor(R.color.core_white);

    int[]      attrs  = {android.R.attr.navigationBarColor};
    TypedArray values = context.obtainStyledAttributes(R.style.TextSecure_LightNoActionBar, attrs);
    int        color  = values.getInt(0, R.color.core_white);

    values.recycle();
    return color;
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
