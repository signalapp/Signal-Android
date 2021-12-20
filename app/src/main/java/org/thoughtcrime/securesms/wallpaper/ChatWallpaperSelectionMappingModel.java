package org.thoughtcrime.securesms.wallpaper;

import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

class ChatWallpaperSelectionMappingModel implements MappingModel<ChatWallpaperSelectionMappingModel> {

  private final ChatWallpaper chatWallpaper;

  ChatWallpaperSelectionMappingModel(@NonNull ChatWallpaper chatWallpaper) {
    this.chatWallpaper = chatWallpaper;
  }

  ChatWallpaper getWallpaper() {
    return chatWallpaper;
  }

  public void loadInto(@NonNull ImageView imageView) {
    chatWallpaper.loadInto(imageView);
  }

  @Override
  public boolean areItemsTheSame(@NonNull ChatWallpaperSelectionMappingModel newItem) {
    return areContentsTheSame(newItem);
  }

  @Override
  public boolean areContentsTheSame(@NonNull ChatWallpaperSelectionMappingModel newItem) {
    return chatWallpaper.equals(newItem.chatWallpaper);
  }
}
