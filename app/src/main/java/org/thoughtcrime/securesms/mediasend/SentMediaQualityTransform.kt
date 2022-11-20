package org.thoughtcrime.securesms.mediasend

import android.content.Context
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.mms.SentMediaQuality
import java.util.Optional

/**
 * Add a [SentMediaQuality] value for [AttachmentDatabase.TransformProperties.getSentMediaQuality] on the
 * transformed media. Safe to use in a pipeline with other transforms.
 */
class SentMediaQualityTransform(
  private val sentMediaQuality: SentMediaQuality
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
      Optional.of(AttachmentDatabase.TransformProperties.forSentMediaQuality(media.transformProperties, sentMediaQuality)))
  }
}