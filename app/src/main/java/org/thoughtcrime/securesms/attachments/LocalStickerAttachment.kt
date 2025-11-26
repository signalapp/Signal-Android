/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import android.net.Uri
import android.os.Parcel
import androidx.core.os.ParcelCompat
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.mms.StickerSlide
import org.thoughtcrime.securesms.stickers.StickerLocator
import java.security.SecureRandom

/**
 * An incoming sticker that is already available locally via an installed sticker pack.
 */
class LocalStickerAttachment : Attachment {

  constructor(
    stickerRecord: StickerRecord,
    stickerLocator: StickerLocator
  ) : super(
    contentType = stickerRecord.contentType,
    transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
    size = stickerRecord.size,
    fileName = null,
    cdn = Cdn.CDN_0,
    remoteLocation = null,
    remoteKey = null,
    remoteDigest = null,
    incrementalDigest = null,
    fastPreflightId = SecureRandom().nextLong().toString(),
    voiceNote = false,
    borderless = false,
    videoGif = false,
    width = StickerSlide.WIDTH,
    height = StickerSlide.HEIGHT,
    incrementalMacChunkSize = 0,
    quote = false,
    quoteTargetContentType = null,
    uploadTimestamp = 0,
    caption = null,
    stickerLocator = stickerLocator,
    blurHash = null,
    audioHash = null,
    transformProperties = null,
    uuid = null
  ) {
    uri = stickerRecord.uri
  }

  @Suppress("unused")
  constructor(parcel: Parcel) : super(parcel) {
    uri = ParcelCompat.readParcelable(parcel, Uri::class.java.classLoader, Uri::class.java)!!
  }

  override val uri: Uri
  override val publicUri: Uri? = null
  override val thumbnailUri: Uri? = null

  val packId: String = stickerLocator!!.packId
  val stickerId: Int = stickerLocator!!.stickerId

  override fun writeToParcel(dest: Parcel, flags: Int) {
    super.writeToParcel(dest, flags)
    dest.writeParcelable(uri, 0)
  }

  override fun equals(other: Any?): Boolean {
    return other != null && other is LocalStickerAttachment && other.uri == uri
  }

  override fun hashCode(): Int {
    return uri.hashCode()
  }
}
