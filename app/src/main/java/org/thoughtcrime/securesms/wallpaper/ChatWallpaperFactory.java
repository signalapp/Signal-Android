package org.thoughtcrime.securesms.wallpaper;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;

/**
 * Converts persisted models of wallpaper into usable {@link ChatWallpaper} instances.
 */
public class ChatWallpaperFactory {

  public static @NonNull ChatWallpaper create(@NonNull Wallpaper model) {
    if (model.hasSingleColor()) {
      return new GradientChatWallpaper(model.getSingleColor().getColor());
    } else if (model.hasLinearGradient()) {
      return buildForLinearGradinent(model.getLinearGradient());
    } else if (model.hasFile()) {
      return buildForFile(model.getFile());
    } else {
      throw new IllegalArgumentException();
    }
  }

  public static @NonNull ChatWallpaper create(@NonNull Uri uri) {
    return new UriChatWallpaper(uri);
  }

  private static @NonNull ChatWallpaper buildForLinearGradinent(@NonNull Wallpaper.LinearGradient gradient) {
    int[] colors = new int[gradient.getColorsCount()];
    for (int i = 0; i < colors.length; i++) {
      colors[i] = gradient.getColors(i);
    }

    float[] positions = new float[gradient.getPositionsCount()];
    for (int i = 0; i < positions.length; i++) {
      positions[i] = gradient.getPositions(i);
    }

    return new GradientChatWallpaper(gradient.getRotation(), colors, positions);
  }

  private static @NonNull ChatWallpaper buildForFile(@NonNull Wallpaper.File file) {
    Uri uri = Uri.parse(file.getUri());
    return new UriChatWallpaper(uri);
  }
}
