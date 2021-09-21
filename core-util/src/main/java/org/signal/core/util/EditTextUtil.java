package org.signal.core.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EditTextUtil {

  private EditTextUtil() {
  }

  public static void addGraphemeClusterLimitFilter(EditText text, int maximumGraphemes) {
    List<InputFilter> filters = new ArrayList<>(Arrays.asList(text.getFilters()));
    filters.add(new GraphemeClusterLimitFilter(maximumGraphemes));
    text.setFilters(filters.toArray(new InputFilter[0]));
  }

  public static void setCursorColor(@NonNull EditText text, @ColorInt int colorInt) {
    if (Build.VERSION.SDK_INT >= 29) {
      Drawable drawable = text.getTextCursorDrawable();

      if (drawable == null) {
        return;
      }

      Drawable cursorDrawable = drawable.mutate();
      cursorDrawable.setColorFilter(new PorterDuffColorFilter(colorInt, PorterDuff.Mode.SRC_IN));
      text.setTextCursorDrawable(cursorDrawable);
    } else {
      setCursorColorViaReflection(text, colorInt);
    }
  }

  /**
   * Note: This is only ever called in API 28 and less.
   */
  @SuppressLint("SoonBlockedPrivateApi")
  private static void setCursorColorViaReflection(EditText editText, int color) {
    try {
      Field fCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
      fCursorDrawableRes.setAccessible(true);

      int   mCursorDrawableRes = fCursorDrawableRes.getInt(editText);
      Field fEditor            = TextView.class.getDeclaredField("mEditor");
      fEditor.setAccessible(true);

      Object   editor          = fEditor.get(editText);
      Class<?> clazz           = editor.getClass();
      Field    fCursorDrawable = clazz.getDeclaredField("mCursorDrawable");

      fCursorDrawable.setAccessible(true);
      Drawable[] drawables = new Drawable[2];

      drawables[0] = editText.getContext().getResources().getDrawable(mCursorDrawableRes);
      drawables[1] = editText.getContext().getResources().getDrawable(mCursorDrawableRes);
      drawables[0].setColorFilter(color, PorterDuff.Mode.SRC_IN);
      drawables[1].setColorFilter(color, PorterDuff.Mode.SRC_IN);

      fCursorDrawable.set(editor, drawables);
    } catch (Throwable ignored) {
    }
  }
}
