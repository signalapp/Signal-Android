package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.model.StickerRecord;

public interface StickerEventListener {
  void onStickerSelected(@NonNull StickerRecord sticker);

  void onStickerManagementClicked();
}
