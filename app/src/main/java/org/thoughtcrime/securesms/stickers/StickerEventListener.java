package org.thoughtcrime.securesms.stickers;

import androidx.annotation.NonNull;

import org.signal.core.models.database.StickerRecord;

public interface StickerEventListener {
  void onStickerSelected(@NonNull StickerRecord sticker);

  void onStickerManagementClicked();
}
