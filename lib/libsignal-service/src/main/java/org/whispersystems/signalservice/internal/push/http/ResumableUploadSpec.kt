package org.whispersystems.signalservice.internal.push.http

import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.protos.resumableuploads.ResumableUpload
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException
import java.io.IOException

/**
 * Contains data around how to begin or resume an upload.
 * For given attachment, this data be saved and reused within the [expirationTimestamp] window.
 */
class ResumableUploadSpec(
  val attachmentKey: ByteArray,
  val attachmentIv: ByteArray,
  val cdnKey: String,
  val cdnNumber: Int,
  val resumeLocation: String,
  val expirationTimestamp: Long,
  val headers: Map<String, String>
) {
  fun toProto(): ResumableUpload {
    return ResumableUpload(
      secretKey = attachmentKey.toByteString(),
      iv = attachmentIv.toByteString(),
      timeout = expirationTimestamp,
      cdnNumber = cdnNumber,
      cdnKey = cdnKey,
      location = resumeLocation,
      headers = headers.entries.map { ResumableUpload.Header(key = it.key, value_ = it.value) }
    )
  }

  fun serialize(): String {
    return Base64.encodeWithPadding(toProto().encode())
  }

  companion object {
    @Throws(ResumeLocationInvalidException::class)
    fun deserialize(serializedSpec: String?): ResumableUploadSpec? {
      try {
        val resumableUpload = ResumableUpload.ADAPTER.decode(Base64.decode(serializedSpec!!))
        return from(resumableUpload)
      } catch (e: IOException) {
        throw ResumeLocationInvalidException()
      }
    }

    @Throws(ResumeLocationInvalidException::class)
    fun from(resumableUpload: ResumableUpload?): ResumableUploadSpec? {
      if (resumableUpload == null) {
        return null
      }

      val headers: MutableMap<String, String> = HashMap()
      for (header in resumableUpload.headers) {
        headers[header.key] = header.value_
      }

      return ResumableUploadSpec(
        attachmentKey = resumableUpload.secretKey.toByteArray(),
        attachmentIv = resumableUpload.iv.toByteArray(),
        cdnKey = resumableUpload.cdnKey,
        cdnNumber = resumableUpload.cdnNumber,
        resumeLocation = resumableUpload.location,
        expirationTimestamp = resumableUpload.timeout,
        headers = headers
      )
    }
  }
}
