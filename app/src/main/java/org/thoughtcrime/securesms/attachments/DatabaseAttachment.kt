package org.thoughtcrime.securesms.attachments

import android.net.Uri
import android.os.Parcel
import androidx.core.os.ParcelCompat
import org.thoughtcrime.securesms.audio.AudioHash
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.ParcelUtil
import java.util.UUID

class DatabaseAttachment : Attachment {

  @JvmField
  val attachmentId: AttachmentId

  @JvmField
  val mmsId: Long

  @JvmField
  val hasData: Boolean

  @JvmField
  val dataHash: String?

  @JvmField
  val archiveCdn: Int

  @JvmField
  val archiveMediaName: String?

  @JvmField
  val archiveMediaId: String?

  @JvmField
  val thumbnailRestoreState: AttachmentTable.ThumbnailRestoreState

  @JvmField
  val archiveTransferState: AttachmentTable.ArchiveTransferState

  private val hasArchiveThumbnail: Boolean
  private val hasThumbnail: Boolean
  val displayOrder: Int

  constructor(
    attachmentId: AttachmentId,
    mmsId: Long,
    hasData: Boolean,
    hasThumbnail: Boolean,
    hasArchiveThumbnail: Boolean,
    contentType: String?,
    transferProgress: Int,
    size: Long,
    fileName: String?,
    cdn: Cdn,
    location: String?,
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
    quote: Boolean,
    caption: String?,
    stickerLocator: StickerLocator?,
    blurHash: BlurHash?,
    audioHash: AudioHash?,
    transformProperties: TransformProperties?,
    displayOrder: Int,
    uploadTimestamp: Long,
    dataHash: String?,
    archiveCdn: Int,
    archiveMediaName: String?,
    archiveMediaId: String?,
    thumbnailRestoreState: AttachmentTable.ThumbnailRestoreState,
    archiveTransferState: AttachmentTable.ArchiveTransferState,
    uuid: UUID?
  ) : super(
    contentType = contentType,
    transferState = transferProgress,
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
    videoGif = videoGif, width = width,
    height = height,
    incrementalMacChunkSize = incrementalMacChunkSize,
    quote = quote,
    uploadTimestamp = uploadTimestamp,
    caption = caption,
    stickerLocator = stickerLocator,
    blurHash = blurHash,
    audioHash = audioHash,
    transformProperties = transformProperties,
    uuid = uuid
  ) {
    this.attachmentId = attachmentId
    this.mmsId = mmsId
    this.hasData = hasData
    this.dataHash = dataHash
    this.hasThumbnail = hasThumbnail
    this.hasArchiveThumbnail = hasArchiveThumbnail
    this.displayOrder = displayOrder
    this.archiveCdn = archiveCdn
    this.archiveMediaName = archiveMediaName
    this.archiveMediaId = archiveMediaId
    this.thumbnailRestoreState = thumbnailRestoreState
    this.archiveTransferState = archiveTransferState
  }

  constructor(parcel: Parcel) : super(parcel) {
    attachmentId = ParcelCompat.readParcelable(parcel, AttachmentId::class.java.classLoader, AttachmentId::class.java)!!
    hasData = ParcelUtil.readBoolean(parcel)
    dataHash = parcel.readString()
    hasThumbnail = ParcelUtil.readBoolean(parcel)
    mmsId = parcel.readLong()
    displayOrder = parcel.readInt()
    archiveCdn = parcel.readInt()
    archiveMediaName = parcel.readString()
    archiveMediaId = parcel.readString()
    hasArchiveThumbnail = ParcelUtil.readBoolean(parcel)
    thumbnailRestoreState = AttachmentTable.ThumbnailRestoreState.deserialize(parcel.readInt())
    archiveTransferState = AttachmentTable.ArchiveTransferState.deserialize(parcel.readInt())
  }

  override fun writeToParcel(dest: Parcel, flags: Int) {
    super.writeToParcel(dest, flags)
    dest.writeParcelable(attachmentId, 0)
    ParcelUtil.writeBoolean(dest, hasData)
    dest.writeString(dataHash)
    ParcelUtil.writeBoolean(dest, hasThumbnail)
    dest.writeLong(mmsId)
    dest.writeInt(displayOrder)
    dest.writeInt(archiveCdn)
    dest.writeString(archiveMediaName)
    dest.writeString(archiveMediaId)
    ParcelUtil.writeBoolean(dest, hasArchiveThumbnail)
    dest.writeInt(thumbnailRestoreState.value)
    dest.writeInt(archiveTransferState.value)
  }

  override val uri: Uri?
    get() = if (hasData || getIncrementalDigest() != null) {
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

  override val thumbnailUri: Uri?
    get() = if (hasArchiveThumbnail) {
      PartAuthority.getAttachmentThumbnailUri(attachmentId)
    } else {
      null
    }

  override fun equals(other: Any?): Boolean {
    return other != null &&
      other is DatabaseAttachment && other.attachmentId == attachmentId && other.uri == uri
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
