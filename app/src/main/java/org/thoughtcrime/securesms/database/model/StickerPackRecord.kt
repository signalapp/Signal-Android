package org.thoughtcrime.securesms.database.model

import android.net.Uri
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.stickers.StickerUrl
import java.util.Optional

/**
 * Represents a record for a sticker pack in the [org.thoughtcrime.securesms.database.StickerTable].
 */
data class StickerPackRecord(
  @JvmField val packId: String,
  @JvmField val packKey: String,
  @JvmField val title: String,
  @JvmField val author: String,
  @JvmField val cover: StickerRecord,
  @JvmField val isInstalled: Boolean
) {
  @JvmField
  val titleOptional: Optional<String> = if (title.isBlank()) Optional.empty() else Optional.of(title)

  @JvmField
  val authorOptional: Optional<String> = if (author.isBlank()) Optional.empty() else Optional.of(author)
}

/**
 * A unique identifier for a sticker pack.
 */
@JvmInline
value class StickerPackId(val value: String)

/**
 * An encryption key for a sticker pack.
 */
@JvmInline
value class StickerPackKey(val value: String)

data class StickerPackParams(
  val id: StickerPackId,
  val key: StickerPackKey
) {
  companion object {
    fun fromExternalUri(uri: Uri?): StickerPackParams? {
      if (uri == null) return null
      return (StickerUrl.parseActionUri(uri) ?: StickerUrl.parseShareLink(uri.toString()))
        .map { parseResult ->
          StickerPackParams(
            id = StickerPackId(parseResult.first),
            key = StickerPackKey(parseResult.second)
          )
        }.orNull()
    }
  }
}
