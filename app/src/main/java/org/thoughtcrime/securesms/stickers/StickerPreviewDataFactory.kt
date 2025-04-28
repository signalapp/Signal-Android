/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import org.thoughtcrime.securesms.database.model.StickerPackRecord
import org.thoughtcrime.securesms.database.model.StickerRecord
import java.util.UUID

/**
 * Generates sample sticker data to use in compose UI previews.
 */
object StickerPreviewDataFactory {
  fun availablePack(
    packId: String = UUID.randomUUID().toString(),
    title: String,
    author: String,
    isBlessed: Boolean = false,
    downloadStatus: AvailableStickerPack.DownloadStatus = AvailableStickerPack.DownloadStatus.NotDownloaded
  ): AvailableStickerPack = AvailableStickerPack(
    record = StickerPackRecord(
      packId = packId,
      packKey = "packKey",
      title = title,
      author = author,
      cover = StickerRecord(
        rowId = 11,
        packId = packId,
        packKey = "packKey",
        stickerId = 111,
        emoji = "",
        contentType = "image/webp",
        size = 1111,
        isCover = true
      ),
      isInstalled = false
    ),
    isBlessed = isBlessed,
    downloadStatus = downloadStatus
  )

  fun installedPack(
    packId: String = UUID.randomUUID().toString(),
    title: String,
    author: String,
    isBlessed: Boolean = false
  ): InstalledStickerPack = InstalledStickerPack(
    record = StickerPackRecord(
      packId = packId,
      packKey = "packKey",
      title = title,
      author = author,
      cover = StickerRecord(
        rowId = 11,
        packId = packId,
        packKey = "packKey",
        stickerId = 111,
        emoji = "",
        contentType = "image/webp",
        size = 1111,
        isCover = true
      ),
      isInstalled = true
    ),
    isBlessed = isBlessed,
    sortOrder = 0
  )
}
