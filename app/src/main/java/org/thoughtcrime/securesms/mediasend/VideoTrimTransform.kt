package org.thoughtcrime.securesms.mediasend

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.core.models.media.TransformProperties
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.edit.video.VideoTrimData

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
