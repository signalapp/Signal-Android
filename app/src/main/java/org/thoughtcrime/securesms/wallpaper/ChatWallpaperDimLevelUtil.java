package org.thoughtcrime.securesms.wallpaper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.SimpleColorFilter;

import org.thoughtcrime.securesms.util.ThemeUtil;

public final class ChatWallpaperDimLevelUtil {

  private ChatWallpaperDimLevelUtil() {
  }

  public static void applyDimLevelForNightMode(@NonNull View dimmer, @NonNull ChatWallpaper chatWallpaper) {
    if (ThemeUtil.isDarkTheme(dimmer.getContext())) {
      dimmer.setAlpha(chatWallpaper.getDimLevelForDarkTheme());
      dimmer.setVisibility(View.VISIBLE);
    } else {
      dimmer.setVisibility(View.GONE);
    }
  }

  public static @Nullable ColorFilter getDimColorFilterForNightMode(@NonNull Context context, @NonNull ChatWallpaper chatWallpaper) {
    if (ThemeUtil.isDarkTheme(context)) {
      int color = Color.argb(Math.round(0xFF * chatWallpaper.getDimLevelForDarkTheme()), 0, 0, 0);
      return new SimpleColorFilter(color);
    } else {
      return null;
    }
  }
}
