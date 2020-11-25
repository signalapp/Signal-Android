package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

public class StickerPackInstallEvent {
  private final Object iconGlideModel;

  public StickerPackInstallEvent(@NonNull Object iconGlideModel) {
    this.iconGlideModel = iconGlideModel;
  }

  public @NonNull Object getIconGlideModel() {
    return iconGlideModel;
  }
}
