package org.thoughtcrime.securesms.mediasend

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties
import org.thoughtcrime.securesms.serialization.UriSerializer

/**
 * Represents a piece of media that the user has on their device.
 */
@Serializable
@Parcelize
data class Media(
  @Serializable(with = UriSerializer::class) val uri: Uri,
  val contentType: String?,
  val date: Long,
  val width: Int,
  val height: Int,
  val size: Long,
  val duration: Long,
  @get:JvmName("isBorderless") val isBorderless: Boolean,
  @get:JvmName("isVideoGif") val isVideoGif: Boolean,
  val bucketId: String?,
  val caption: String?,
  val transformProperties: TransformProperties?,
  val fileName: String?
) : Parcelable {
  companion object {
    const val ALL_MEDIA_BUCKET_ID: String = "org.thoughtcrime.securesms.ALL_MEDIA"
  }

  fun withMimeType(newMimeType: String) = copy(contentType = newMimeType)
}
