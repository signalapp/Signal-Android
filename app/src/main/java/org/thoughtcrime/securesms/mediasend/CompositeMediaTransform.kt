package org.thoughtcrime.securesms.mediasend

import android.content.Context

/**
 * Allow multiple transforms to operate on [Media]. Care should
 * be taken on the order and implementation of combined transformers to prevent
 * one undoing the work of the other.
 */
class CompositeMediaTransform(
  private vararg val transforms: MediaTransform
) : MediaTransform {

  override fun transform(context: Context, media: Media): Media {
    return transforms.fold(media) { updatedMedia, transform ->
      transform.transform(context, updatedMedia)
    }
  }
}