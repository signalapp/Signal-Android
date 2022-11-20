package org.thoughtcrime.securesms.mediasend

import android.content.Context
import org.thoughtcrime.securesms.database.AttachmentDatabase.TransformProperties
import org.thoughtcrime.securesms.mms.SentMediaQuality
import java.util.Optional

class VideoTrimTransform(
  private val data: VideoEditorFragment.Data
) : MediaTransform {

  override fun transform(context: Context, media: Media): Media {
    return Media(media.uri,
      media.mimeType,
      media.date,
      media.width,
      media.height,
      media.size,
      media.duration,
      media.isBorderless,
      media.isVideoGif,
      media.bucketId,
      media.caption,
      Optional.of(TransformProperties(false, data.durationEdited, data.startTimeUs, data.endTimeUs, SentMediaQuality.STANDARD.code)))
  }
}