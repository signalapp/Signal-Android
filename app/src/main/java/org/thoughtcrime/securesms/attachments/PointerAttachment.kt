package org.thoughtcrime.securesms.attachments

import android.net.Uri
import android.os.Parcel
import androidx.annotation.VisibleForTesting
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.whispersystems.signalservice.api.InvalidMessageStructureException
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment
import org.whispersystems.signalservice.api.util.AttachmentPointerUtil
import org.whispersystems.signalservice.internal.push.DataMessage
import java.util.Optional
import java.util.UUID

class PointerAttachment : Attachment {
  @VisibleForTesting
  constructor(
    contentType: String?,
    transferState: Int,
    size: Long,
    fileName: String?,
    cdn: Cdn,
    location: String,
    key: String?,
    iv: ByteArray?,
    digest: ByteArray?,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int,
    fastPreflightId: String?,
    voiceNote: Boolean,
    borderless: Boolean,
    videoGif: Boolean,
    width: Int,
    height: Int,
    uploadTimestamp: Long,
    caption: String?,
    stickerLocator: StickerLocator?,
    blurHash: BlurHash?,
    uuid: UUID?
  ) : super(
    contentType = contentType,
    transferState = transferState,
    size = size,
    fileName = fileName,
    cdn = cdn,
    remoteLocation = location,
    remoteKey = key,
    remoteIv = iv,
    remoteDigest = digest,
    incrementalDigest = incrementalDigest,
    fastPreflightId = fastPreflightId,
    voiceNote = voiceNote,
    borderless = borderless,
    videoGif = videoGif,
    width = width,
    height = height,
    incrementalMacChunkSize = incrementalMacChunkSize,
    quote = false,
    uploadTimestamp = uploadTimestamp,
    caption = caption,
    stickerLocator = stickerLocator,
    blurHash = blurHash,
    audioHash = null,
    transformProperties = null,
    uuid = uuid
  )

  constructor(parcel: Parcel) : super(parcel)

  override val uri: Uri? = null
  override val publicUri: Uri? = null
  override val thumbnailUri: Uri? = null

  companion object {
    @JvmStatic
    fun forPointers(pointers: Optional<List<SignalServiceAttachment>>): List<Attachment> {
      if (!pointers.isPresent) {
        return emptyList()
      }

      return pointers.get()
        .map { forPointer(Optional.ofNullable(it)) }
        .filter { it.isPresent }
        .map { it.get() }
    }

    @JvmStatic
    @JvmOverloads
    fun forPointer(
      pointer: Optional<SignalServiceAttachment>,
      stickerLocator: StickerLocator? = null,
      fastPreflightId: String? = null,
      transferState: Int = AttachmentTable.TRANSFER_PROGRESS_PENDING
    ): Optional<Attachment> {
      if (!pointer.isPresent || !pointer.get().isPointer()) {
        return Optional.empty()
      }

      val encodedKey: String? = pointer.get().asPointer().key?.let { Base64.encodeWithPadding(it) }

      return Optional.of(
        PointerAttachment(
          contentType = pointer.get().contentType,
          transferState = transferState,
          size = pointer.get().asPointer().size.orElse(0).toLong(),
          fileName = pointer.get().asPointer().fileName.orElse(null),
          cdn = Cdn.fromCdnNumber(pointer.get().asPointer().cdnNumber),
          location = pointer.get().asPointer().remoteId.toString(),
          key = encodedKey,
          iv = null,
          digest = pointer.get().asPointer().digest.orElse(null),
          incrementalDigest = pointer.get().asPointer().incrementalDigest.orElse(null),
          incrementalMacChunkSize = pointer.get().asPointer().incrementalMacChunkSize,
          fastPreflightId = fastPreflightId,
          voiceNote = pointer.get().asPointer().voiceNote,
          borderless = pointer.get().asPointer().isBorderless,
          videoGif = pointer.get().asPointer().isGif,
          width = pointer.get().asPointer().width,
          height = pointer.get().asPointer().height,
          uploadTimestamp = pointer.get().asPointer().uploadTimestamp,
          caption = pointer.get().asPointer().caption.orElse(null),
          stickerLocator = stickerLocator,
          blurHash = BlurHash.parseOrNull(pointer.get().asPointer().blurHash.orElse(null)),
          uuid = pointer.get().asPointer().uuid
        )
      )
    }

    fun forPointer(quotedAttachment: DataMessage.Quote.QuotedAttachment): Optional<Attachment> {
      val thumbnail: SignalServiceAttachment? = try {
        if (quotedAttachment.thumbnail != null) {
          AttachmentPointerUtil.createSignalAttachmentPointer(quotedAttachment.thumbnail)
        } else {
          null
        }
      } catch (e: InvalidMessageStructureException) {
        return Optional.empty()
      }

      return Optional.of(
        PointerAttachment(
          contentType = quotedAttachment.contentType!!,
          transferState = AttachmentTable.TRANSFER_PROGRESS_PENDING,
          size = (if (thumbnail != null) thumbnail.asPointer().size.orElse(0) else 0).toLong(),
          fileName = quotedAttachment.fileName,
          cdn = Cdn.fromCdnNumber(thumbnail?.asPointer()?.cdnNumber ?: 0),
          location = thumbnail?.asPointer()?.remoteId?.toString() ?: "0",
          key = thumbnail?.asPointer()?.key?.let { Base64.encodeWithPadding(it) },
          iv = null,
          digest = thumbnail?.asPointer()?.digest?.orElse(null),
          incrementalDigest = thumbnail?.asPointer()?.incrementalDigest?.orElse(null),
          incrementalMacChunkSize = thumbnail?.asPointer()?.incrementalMacChunkSize ?: 0,
          fastPreflightId = null,
          voiceNote = false,
          borderless = false,
          videoGif = false,
          width = thumbnail?.asPointer()?.width ?: 0,
          height = thumbnail?.asPointer()?.height ?: 0,
          uploadTimestamp = thumbnail?.asPointer()?.uploadTimestamp ?: 0,
          caption = thumbnail?.asPointer()?.caption?.orElse(null),
          stickerLocator = null,
          blurHash = null,
          uuid = thumbnail?.asPointer()?.uuid
        )
      )
    }
  }
}
