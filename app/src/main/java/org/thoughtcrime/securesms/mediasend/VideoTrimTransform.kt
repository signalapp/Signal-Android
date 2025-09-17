package org.thoughtcrime.securesms.mediasend

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties
import org.thoughtcrime.securesms.mediasend.v2.videos.VideoTrimData
import org.thoughtcrime.securesms.mms.SentMediaQuality

class VideoTrimTransform(private val data: VideoTrimData) : MediaTransform {
  @WorkerThread
  override fun transform(context: Context, media: Media): Media {
    return Media(
      uri = media.uri,
      contentType = media.contentType,
      date = media.date,
      width = media.width,
      height = media.height,
      size = media.size,
      duration = media.duration,
      isBorderless = media.isBorderless,
      isVideoGif = media.isVideoGif,
      bucketId = media.bucketId,
      caption = media.caption,
      transformProperties = TransformProperties(false, data.isDurationEdited, data.startTimeUs, data.endTimeUs, SentMediaQuality.STANDARD.code, false),
      fileName = media.fileName
    )
  }
}
