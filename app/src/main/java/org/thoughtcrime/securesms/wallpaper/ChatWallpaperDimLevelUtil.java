package org.thoughtcrime.securesms.wallpaper;

import android.view.View;

import androidx.annotation.NonNull;

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
}
