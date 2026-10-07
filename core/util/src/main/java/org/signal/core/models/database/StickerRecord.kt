package org.signal.core.models.database

import android.net.Uri
import org.signal.core.util.PartAuthorityUris

/**
 * Represents a record for a sticker pack in the sticker tables.
 */
data class StickerRecord(
  @JvmField val rowId: Long,
  @JvmField val packId: String,
  @JvmField val packKey: String,
  @JvmField val stickerId: Int,
  @JvmField val emoji: String,
  @JvmField val contentType: String,
  @JvmField val size: Long,
  @JvmField val isCover: Boolean,
  @JvmField val isFavorite: Boolean = false
) {
  @JvmField
  val uri: Uri = PartAuthorityUris.getStickerUri(rowId)
}

/**
 * Represents a favorited sticker that is synced via storage service. Can include recently unfavorited stickers too.
 */
data class FavoriteStickerSyncRecord(
  val rowId: Long,
  val packId: String,
  val packKey: String,
  val stickerId: Int,
  val favoritedAt: Long,
  val unfavoritedAt: Long,
  val storageServiceId: ByteArray?,
  val storageServiceProto: ByteArray?
)
