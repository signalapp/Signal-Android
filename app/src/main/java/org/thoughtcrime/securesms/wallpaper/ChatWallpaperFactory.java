package org.thoughtcrime.securesms.wallpaper;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

/**
 * Converts persisted models of wallpaper into usable {@link ChatWallpaper} instances.
 */
public final class ChatWallpaperFactory {

  private ChatWallpaperFactory() {}

  public static @NonNull ChatWallpaper create(@NonNull Wallpaper model) {
    if (model.singleColor != null) {
      return buildForSingleColor(model.singleColor, model.dimLevelInDarkTheme);
    } else if (model.linearGradient != null) {
      return buildForLinearGradinent(model.linearGradient, model.dimLevelInDarkTheme);
    } else if (model.file_ != null) {
      return buildForFile(model.file_, model.dimLevelInDarkTheme);
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static @NonNull ChatWallpaper updateWithDimming(@NonNull ChatWallpaper wallpaper, float dimLevelInDarkTheme) {
    Wallpaper model = wallpaper.serialize();

    return create(model.newBuilder().dimLevelInDarkTheme(dimLevelInDarkTheme).build());
  }

  public static @NonNull ChatWallpaper create(@NonNull Uri uri) {
    return new UriChatWallpaper(uri, 0f);
  }

  private static @NonNull ChatWallpaper buildForSingleColor(@NonNull Wallpaper.SingleColor singleColor, float dimLevelInDarkTheme) {
    return new SingleColorChatWallpaper(singleColor.color, dimLevelInDarkTheme);
  }

  private static @NonNull ChatWallpaper buildForLinearGradinent(@NonNull Wallpaper.LinearGradient gradient, float dimLevelInDarkTheme) {
    int[] colors = new int[gradient.colors.size()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = gradient.colors.get(i);
    }

    float[] positions = new float[gradient.positions.size()];
    for (int i = 0; i < positions.length; i++) {
      positions[i] = gradient.positions.get(i);
    }

    return new GradientChatWallpaper(gradient.rotation, colors, positions, dimLevelInDarkTheme);
  }

  private static @NonNull ChatWallpaper buildForFile(@NonNull Wallpaper.File file, float dimLevelInDarkTheme) {
    Uri uri = Uri.parse(file.uri);
    return new UriChatWallpaper(uri, dimLevelInDarkTheme);
  }
}
