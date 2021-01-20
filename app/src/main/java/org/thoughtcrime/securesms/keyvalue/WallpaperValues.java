package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.model.databaseprotos.Wallpaper;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperFactory;
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage;

public final class WallpaperValues extends SignalStoreValues {

  private static final String TAG = Log.tag(WallpaperValues.class);

  private static final String KEY_WALLPAPER = "wallpaper.wallpaper";

  WallpaperValues(@NonNull KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {

  }

  public void setWallpaper(@NonNull Context context, @Nullable ChatWallpaper wallpaper) {
    Wallpaper currentWallpaper = getCurrentWallpaper();
    Uri       currentUri       = null;

    if (currentWallpaper != null && currentWallpaper.hasFile()) {
      currentUri = Uri.parse(currentWallpaper.getFile().getUri());
    }

    if (wallpaper != null) {
      putBlob(KEY_WALLPAPER, wallpaper.serialize().toByteArray());
    } else {
      getStore().beginWrite().remove(KEY_WALLPAPER).apply();
    }

    WallpaperStorage.onWallpaperDeselected(context, currentUri);
  }

  public @Nullable ChatWallpaper getWallpaper() {
    Wallpaper currentWallpaper = getCurrentWallpaper();

    if (currentWallpaper != null) {
      return ChatWallpaperFactory.create(currentWallpaper);
    } else {
      return null;
    }
  }

  public @Nullable Uri getCurrentWallpaperUri() {
    Wallpaper currentWallpaper = getCurrentWallpaper();

    if (currentWallpaper != null && currentWallpaper.hasFile()) {
      return Uri.parse(currentWallpaper.getFile().getUri());
    } else {
      return null;
    }
  }

  private @Nullable Wallpaper getCurrentWallpaper() {
    byte[] serialized = getBlob(KEY_WALLPAPER, null);

    if (serialized != null) {
      try {
        return Wallpaper.parseFrom(serialized);
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "Invalid proto stored for wallpaper!");
        return null;
      }
    } else {
      return null;
    }
  }
}
