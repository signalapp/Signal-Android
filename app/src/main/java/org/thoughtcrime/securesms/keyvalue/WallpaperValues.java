package org.thoughtcrime.securesms.keyvalue;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperFactory;
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class WallpaperValues extends SignalStoreValues {

  private static final String TAG = Log.tag(WallpaperValues.class);

  private static final String KEY_WALLPAPER = "wallpaper.wallpaper";

  WallpaperValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  public void setWallpaper(@Nullable ChatWallpaper wallpaper) {
    Wallpaper currentWallpaper = getCurrentRawWallpaper();
    Uri       currentUri       = null;

    if (currentWallpaper != null && currentWallpaper.file_ != null) {
      currentUri = Uri.parse(currentWallpaper.file_.uri);
    }

    if (wallpaper != null) {
      putBlob(KEY_WALLPAPER, wallpaper.serialize().encode());
    } else {
      getStore().beginWrite().remove(KEY_WALLPAPER).apply();
    }

    if (currentUri != null) {
      WallpaperStorage.onWallpaperDeselected(currentUri);
    }
  }

  public @Nullable ChatWallpaper getWallpaper() {
    Wallpaper currentWallpaper = getCurrentRawWallpaper();

    if (currentWallpaper != null) {
      return ChatWallpaperFactory.create(currentWallpaper);
    } else {
      return null;
    }
  }

  public boolean hasWallpaperSet() {
    return getStore().getBlob(KEY_WALLPAPER, null) != null;
  }

  public void setDimInDarkTheme(boolean enabled) {
    Wallpaper currentWallpaper = getCurrentRawWallpaper();

    if (currentWallpaper != null) {
      putBlob(KEY_WALLPAPER,
              currentWallpaper.newBuilder()
                              .dimLevelInDarkTheme(enabled ? 0.2f : 0)
                              .build()
                              .encode());
    } else {
      throw new IllegalStateException("No wallpaper currently set!");
    }
  }


  /**
   * Retrieves the URI of the current wallpaper. Note that this will only return a value if the
   * wallpaper is both set *and* it's an image.
   */
  public @Nullable Uri getWallpaperUri() {
    Wallpaper currentWallpaper = getCurrentRawWallpaper();

    if (currentWallpaper != null && currentWallpaper.file_ != null) {
      return Uri.parse(currentWallpaper.file_.uri);
    } else {
      return null;
    }
  }

  /**
   * Allows for retrieval of the raw, serialized wallpaper proto. Clients should prefer {@link #getWallpaper()} instead.
   */
  public @Nullable Wallpaper getCurrentRawWallpaper() {
    byte[] serialized = getBlob(KEY_WALLPAPER, null);

    if (serialized != null) {
      try {
        return Wallpaper.ADAPTER.decode(serialized);
      } catch (IOException e) {
        Log.w(TAG, "Invalid proto stored for wallpaper!");
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * For a migration, we need to update the current wallpaper _without_ triggering the onDeselectedEvents and such.
   * For normal usage, use {@link #setWallpaper(ChatWallpaper)}
   */
  public void setRawWallpaperForMigration(@NonNull Wallpaper wallpaper) {
    putBlob(KEY_WALLPAPER, wallpaper.encode());
  }
}
