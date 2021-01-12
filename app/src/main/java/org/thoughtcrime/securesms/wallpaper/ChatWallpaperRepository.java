package org.thoughtcrime.securesms.wallpaper;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import java.util.List;

class ChatWallpaperRepository {

  void getAllWallpaper(@NonNull Consumer<List<ChatWallpaper>> consumer) {
    consumer.accept(ChatWallpaper.BUILTINS);
  }

}
