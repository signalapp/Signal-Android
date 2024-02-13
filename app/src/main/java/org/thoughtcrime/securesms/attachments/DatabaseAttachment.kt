package org.thoughtcrime.securesms.attachments

import android.net.Uri
import android.os.Parcel
import androidx.core.os.ParcelCompat
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.ParcelUtil

class DatabaseAttachment : Attachment {

  @JvmField
  val attachmentId: AttachmentId

  @JvmField
  val mmsId: Long

  @JvmField
  val hasData: Boolean

  private val hasThumbnail: Boolean
  val displayOrder: Int

  constructor(
    attachmentId: AttachmentId,
    mmsId: Long,
    hasData: Boolean,
    hasThumbnail: Boolean,
    contentType: String?,
    transferProgress: Int,
    size: Long,
    fileName: String?,
    cdnNumber: Int,
    location: String?,
    key: String?,
    digest: ByteArray?,
    incrementalDigest: ByteArray?,
    incrementalMacChunkSize: Int,
    fastPreflightId: String?,
    voiceNote: Boolean,
    borderless: Boolean,
    videoGif: Boolean,
    width: Int,
    height: Int,
    quote: Boolean,
    caption: String?,
    stickerLocator: StickerLocator?,
    blurHash: BlurHash?,
    audioHash: AudioHash?,
    transformProperties: TransformProperties?,
    displayOrder: Int,
    uploadTimestamp: Long
  ) : super(
    contentType = contentType!!,
    transferState = transferProgress,
    size = size,
    fileName = fileName,
    cdnNumber = cdnNumber,
    remoteLocation = location,
    remoteKey = key,
    remoteDigest = digest,
    incrementalDigest = incrementalDigest,
    fastPreflightId = fastPreflightId,
    voiceNote = voiceNote,
    borderless = borderless,
    videoGif = videoGif, width = width,
    height = height,
    incrementalMacChunkSize = incrementalMacChunkSize,
    quote = quote,
    uploadTimestamp = uploadTimestamp,
    caption = caption,
    stickerLocator = stickerLocator,
    blurHash = blurHash,
    audioHash = audioHash,
    transformProperties = transformProperties
  ) {
    this.attachmentId = attachmentId
    this.mmsId = mmsId
    this.hasData = hasData
    this.hasThumbnail = hasThumbnail
    this.displayOrder = displayOrder
  }

  constructor(parcel: Parcel) : super(parcel) {
    attachmentId = ParcelCompat.readParcelable(parcel, AttachmentId::class.java.classLoader, AttachmentId::class.java)!!
    hasData = ParcelUtil.readBoolean(parcel)
    hasThumbnail = ParcelUtil.readBoolean(parcel)
    mmsId = parcel.readLong()
    displayOrder = parcel.readInt()
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    super.writeToParcel(dest, flags)
    dest.writeParcelable(attachmentId, 0)
    ParcelUtil.writeBoolean(dest, hasData)
    ParcelUtil.writeBoolean(dest, hasThumbnail)
    dest.writeLong(mmsId)
    dest.writeInt(displayOrder)
  }

  override val uri: Uri?
    get() = if (hasData || FeatureFlags.instantVideoPlayback() && getIncrementalDigest() != null) {
      PartAuthority.getAttachmentDataUri(attachmentId)
    } else {
      null
    }

  override val publicUri: Uri?
    get() = if (hasData) {
      PartAuthority.getAttachmentPublicUri(uri)
    } else {
      null
    }

  override fun equals(other: Any?): Boolean {
    return other != null &&
      other is DatabaseAttachment && other.attachmentId == attachmentId
  }

  override fun hashCode(): Int {
    return attachmentId.hashCode()
  }

  class DisplayOrderComparator : Comparator<DatabaseAttachment> {
    override fun compare(lhs: DatabaseAttachment, rhs: DatabaseAttachment): Int {
      return lhs.displayOrder.compareTo(rhs.displayOrder)
    }
  }
}
