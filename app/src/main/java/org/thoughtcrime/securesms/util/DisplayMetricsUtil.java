package org.thoughtcrime.securesms.util;

import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

public final class DisplayMetricsUtil {
  private DisplayMetricsUtil() {
  }

  public static void forceAspectRatioToScreenByAdjustingHeight(@NonNull DisplayMetrics displayMetrics, @NonNull View view) {
    int screenHeight = displayMetrics.heightPixels;
    int screenWidth  = displayMetrics.widthPixels;

    ViewGroup.LayoutParams params = view.getLayoutParams();
    params.height = params.width * screenHeight / screenWidth;
    view.setLayoutParams(params);
  }
}
