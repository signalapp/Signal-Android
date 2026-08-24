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
  @JvmField val isCover: Boolean
) {
  @JvmField
  val uri: Uri = PartAuthorityUris.getStickerUri(rowId)
}
