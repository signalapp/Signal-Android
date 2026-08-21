package org.thoughtcrime.securesms.mediasend

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.models.media.Media
import org.signal.core.models.media.TransformProperties
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.screens.edit.video.VideoTrimData

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
      transformProperties = TransformProperties(
        skipTransform = false,
        videoTrim = data.isDurationEdited,
        videoTrimStartTimeUs = if (data.isDurationEdited) data.startTimeUs else 0,
        videoTrimEndTimeUs = if (data.isDurationEdited) data.endTimeUs else 0,
        sentMediaQuality = SentMediaQuality.STANDARD.code,
        mp4FastStart = false,
        videoMuted = data.isMuted
      ),
      fileName = media.fileName
    )
  }
}
