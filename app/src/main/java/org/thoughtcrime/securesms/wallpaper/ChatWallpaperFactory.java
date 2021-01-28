package org.thoughtcrime.securesms.wallpaper;

import android.net.Uri;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

/**
 * Converts persisted models of wallpaper into usable {@link ChatWallpaper} instances.
 */
public final class ChatWallpaperFactory {

  private ChatWallpaperFactory() {}

  public static @NonNull ChatWallpaper create(@NonNull Wallpaper model) {
    if (model.hasSingleColor()) {
      return buildForSingleColor(model.getSingleColor(), model.getDimLevelInDarkTheme());
    } else if (model.hasLinearGradient()) {
      return buildForLinearGradinent(model.getLinearGradient(), model.getDimLevelInDarkTheme());
    } else if (model.hasFile()) {
      return buildForFile(model.getFile(), model.getDimLevelInDarkTheme());
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static @NonNull ChatWallpaper updateWithDimming(@NonNull ChatWallpaper wallpaper, float dimLevelInDarkTheme) {
    Wallpaper model = wallpaper.serialize();

    return create(model.toBuilder().setDimLevelInDarkTheme(dimLevelInDarkTheme).build());
  }

  public static @NonNull ChatWallpaper create(@NonNull Uri uri) {
    return new UriChatWallpaper(uri, 0f);
  }

  private static @NonNull ChatWallpaper buildForSingleColor(@NonNull Wallpaper.SingleColor singleColor, float dimLevelInDarkTheme) {
    return new SingleColorChatWallpaper(singleColor.getColor(), dimLevelInDarkTheme);
  }

  private static @NonNull ChatWallpaper buildForLinearGradinent(@NonNull Wallpaper.LinearGradient gradient, float dimLevelInDarkTheme) {
    int[] colors = new int[gradient.getColorsCount()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = gradient.getColors(i);
    }

    float[] positions = new float[gradient.getPositionsCount()];
    for (int i = 0; i < positions.length; i++) {
      positions[i] = gradient.getPositions(i);
    }

    return new GradientChatWallpaper(gradient.getRotation(), colors, positions, dimLevelInDarkTheme);
  }

  private static @NonNull ChatWallpaper buildForFile(@NonNull Wallpaper.File file, float dimLevelInDarkTheme) {
    Uri uri = Uri.parse(file.getUri());
    return new UriChatWallpaper(uri, dimLevelInDarkTheme);
  }
}
