package org.thoughtcrime.securesms.util;

import android.text.Layout;

import androidx.annotation.NonNull;

/**
 * Utility functions for dealing with {@link Layout}.
 *
 * Ported and modified from https://github.com/googlearchive/android-text/tree/master/RoundedBackground-Kotlin
 */
public class LayoutUtil {
  private static final float DEFAULT_LINE_SPACING_EXTRA = 0f;

  private static final float DEFAULT_LINE_SPACING_MULTIPLIER = 1f;

  public static int getLineHeight(@NonNull Layout layout, int line) {
    return layout.getLineTop(line + 1) - layout.getLineTop(line);
  }

  public static int getLineTopWithoutPadding(@NonNull Layout layout, int line) {
    int lineTop = layout.getLineTop(line);
    if (line == 0) {
      lineTop -= layout.getTopPadding();
    }
    return lineTop;
  }

  public static int getLineBottomWithoutPadding(@NonNull Layout layout, int line) {
    int lineBottom = getLineBottomWithoutSpacing(layout, line);
    if (line == layout.getLineCount() - 1) {
      lineBottom -= layout.getBottomPadding();
    }
    return lineBottom;
  }

  public static int getLineBottomWithoutSpacing(@NonNull Layout layout, int line) {
    int     lineBottom            = layout.getLineBottom(line);
    boolean isLastLine            = line == layout.getLineCount() - 1;
    float   lineSpacingExtra      = layout.getSpacingAdd();
    float   lineSpacingMultiplier = layout.getSpacingMultiplier();
    boolean hasLineSpacing        = lineSpacingExtra != DEFAULT_LINE_SPACING_EXTRA || lineSpacingMultiplier != DEFAULT_LINE_SPACING_MULTIPLIER;

    int lineBottomWithoutSpacing;
    if (!hasLineSpacing || isLastLine) {
      lineBottomWithoutSpacing = lineBottom;
    } else {
      float extra;
      if (Float.compare(lineSpacingMultiplier, DEFAULT_LINE_SPACING_MULTIPLIER) != 0) {
        int lineHeight = getLineHeight(layout, line);
        extra = lineHeight - (lineHeight - lineSpacingExtra) / lineSpacingMultiplier;
      } else {
        extra = lineSpacingExtra;
      }

      lineBottomWithoutSpacing = (int) (lineBottom - extra);
    }

    return lineBottomWithoutSpacing;
  }
}
