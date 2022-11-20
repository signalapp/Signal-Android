package org.thoughtcrime.securesms.mediasend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import org.signal.core.util.StreamUtil
import org.signal.core.util.logging.Log
import org.signal.imageeditor.core.model.EditorModel
import org.thoughtcrime.securesms.fonts.FontTypefaceProvider
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Optional

class ImageEditorModelRenderMediaTransform(
  private val modelToRender: EditorModel,
  private val size: Point? = null
) : MediaTransform {

  override fun transform(context: Context, media: Media): Media {
    val outputStream = ByteArrayOutputStream()
    val bitmap = modelToRender.render(context, size, FontTypefaceProvider())

    try {
      bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

      val uri = BlobProvider.getInstance()
        .forData(outputStream.toByteArray())
        .withMimeType(MediaUtil.IMAGE_JPEG)
        .createForSingleSessionOnDisk(context)

      return Media(uri, MediaUtil.IMAGE_JPEG, media.date, bitmap.width, bitmap.height, outputStream.size().toLong(), 0, false, false, media.bucketId, media.caption, Optional.empty())
    } catch (e: IOException) {
      Log.w(TAG, "Failed to render image. Using base image.")
      return media
    } finally {
      bitmap.recycle()
      StreamUtil.close(outputStream)
    }
  }

  companion object {
    private val TAG = Log.tag(ImageEditorModelRenderMediaTransform::class.java)
  }
}