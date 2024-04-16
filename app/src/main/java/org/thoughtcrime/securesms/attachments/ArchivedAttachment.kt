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

class ArchivedAttachment : Attachment {

  @JvmField
  val archiveCdn: Int

  @JvmField
  val archiveMediaName: String

  @JvmField
  val archiveMediaId: String

  constructor(
    contentType: String?,
    size: Long,
    cdn: Int,
    key: ByteArray,
    cdnKey: String?,
    archiveCdn: Int?,
    archiveMediaName: String,
    archiveMediaId: String,
    digest: ByteArray,
    incrementalMac: ByteArray?,
    incrementalMacChunkSize: Int?,
    width: Int?,
    height: Int?,
    caption: String?,
    blurHash: String?,
    voiceNote: Boolean,
    borderless: Boolean,
    gif: Boolean,
    quote: Boolean
  ) : super(
    contentType = contentType ?: "",
    quote = quote,
    transferState = AttachmentTable.TRANSFER_NEEDS_RESTORE,
    size = size,
    fileName = null,
    cdn = Cdn.fromCdnNumber(cdn),
    remoteLocation = cdnKey,
    remoteKey = Base64.encodeWithoutPadding(key),
    remoteDigest = digest,
    incrementalDigest = incrementalMac,
    fastPreflightId = null,
    voiceNote = voiceNote,
    borderless = borderless,
    videoGif = gif,
    width = width ?: 0,
    height = height ?: 0,
    incrementalMacChunkSize = incrementalMacChunkSize ?: 0,
    uploadTimestamp = 0,
    caption = caption,
    stickerLocator = null,
    blurHash = BlurHash.parseOrNull(blurHash),
    audioHash = null,
    transformProperties = null
  ) {
    this.archiveCdn = archiveCdn ?: Cdn.CDN_3.cdnNumber
    this.archiveMediaName = archiveMediaName
    this.archiveMediaId = archiveMediaId
  }

  constructor(parcel: Parcel) : super(parcel) {
    archiveCdn = parcel.readInt()
    archiveMediaName = parcel.readString()!!
    archiveMediaId = parcel.readString()!!
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    super.writeToParcel(dest, flags)
    dest.writeInt(archiveCdn)
    dest.writeString(archiveMediaName)
    dest.writeString(archiveMediaId)
  }

  override val uri: Uri? = null
  override val publicUri: Uri? = null
}
