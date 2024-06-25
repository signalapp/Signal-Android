package org.thoughtcrime.securesms.database

import android.net.Uri
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.stickers.StickerLocator
import java.util.UUID

object UriAttachmentBuilder {
  fun build(
    id: Long,
    uri: Uri = Uri.parse("content://$id"),
    contentType: String,
    transferState: Int = AttachmentTable.TRANSFER_PROGRESS_PENDING,
    size: Long = 0L,
    fileName: String = "file$id",
    voiceNote: Boolean = false,
    borderless: Boolean = false,
    videoGif: Boolean = false,
    quote: Boolean = false,
    caption: String? = null,
    stickerLocator: StickerLocator? = null,
    blurHash: BlurHash? = null,
    audioHash: AudioHash? = null,
    transformProperties: AttachmentTable.TransformProperties? = null,
    uuid: UUID? = UUID.randomUUID()
  ): UriAttachment {
    return UriAttachment(
      dataUri = uri,
      contentType = contentType,
      transferState = transferState,
      size = size,
      width = 0,
      height = 0,
      fileName = fileName,
      fastPreflightId = null,
      voiceNote = voiceNote,
      borderless = borderless,
      videoGif = videoGif,
      quote = quote,
      caption = caption,
      stickerLocator = stickerLocator,
      blurHash = blurHash,
      audioHash = audioHash,
      transformProperties = transformProperties,
      uuid = uuid
    )
  }
}
