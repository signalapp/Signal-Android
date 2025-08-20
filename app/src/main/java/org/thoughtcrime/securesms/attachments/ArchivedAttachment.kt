/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.net.Uri
import android.os.Parcel
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.stickers.StickerLocator
import java.util.UUID

class ArchivedAttachment : Attachment {

  companion object {
    private const val NO_ARCHIVE_CDN = -404
  }

  @JvmField
  val archiveCdn: Int?

  @JvmField
  val plaintextHash: ByteArray

  constructor(
    contentType: String?,
    size: Long,
    cdn: Int,
    uploadTimestamp: Long?,
    key: ByteArray,
    cdnKey: String?,
    archiveCdn: Int?,
    plaintextHash: ByteArray,
    incrementalMac: ByteArray?,
    incrementalMacChunkSize: Int?,
    width: Int?,
    height: Int?,
    caption: String?,
    blurHash: String?,
    voiceNote: Boolean,
    borderless: Boolean,
    stickerLocator: StickerLocator?,
    gif: Boolean,
    quote: Boolean,
    uuid: UUID?,
    fileName: String?
  ) : super(
    contentType = contentType ?: "",
    quote = quote,
    transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
    size = size,
    fileName = fileName,
    cdn = Cdn.fromCdnNumber(cdn),
    remoteLocation = cdnKey,
    remoteKey = Base64.encodeWithPadding(key),
    remoteDigest = null,
    incrementalDigest = incrementalMac,
    fastPreflightId = null,
    voiceNote = voiceNote,
    borderless = borderless,
    videoGif = gif,
    width = width ?: 0,
    height = height ?: 0,
    incrementalMacChunkSize = incrementalMacChunkSize ?: 0,
    uploadTimestamp = uploadTimestamp ?: 0,
    caption = caption,
    stickerLocator = stickerLocator,
    blurHash = BlurHash.parseOrNull(blurHash),
    audioHash = null,
    transformProperties = null,
    uuid = uuid
  ) {
    this.archiveCdn = archiveCdn
    this.plaintextHash = plaintextHash
  }

  constructor(parcel: Parcel) : super(parcel) {
    archiveCdn = parcel.readInt().takeIf { it != NO_ARCHIVE_CDN }
    plaintextHash = parcel.createByteArray()!!
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    super.writeToParcel(dest, flags)
    dest.writeInt(archiveCdn ?: NO_ARCHIVE_CDN)
    dest.writeByteArray(plaintextHash)
  }

  override val uri: Uri? = null
  override val publicUri: Uri? = null
  override val thumbnailUri: Uri? = null
}
