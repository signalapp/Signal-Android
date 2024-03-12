package org.thoughtcrime.securesms.media

import android.content.Context
import android.net.Uri
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.PartUriParser
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.video.interfaces.MediaInput
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.MediaDataSourceMediaInput
import java.io.IOException

/**
 * A media input source that is decrypted on the fly.
 */
@RequiresApi(api = 23)
object DecryptableUriMediaInput {
  @JvmStatic
  @Throws(IOException::class)
  fun createForUri(context: Context, uri: Uri): MediaInput {
    if (BlobProvider.isAuthority(uri)) {
      return MediaDataSourceMediaInput(BlobProvider.getInstance().getMediaDataSource(context, uri))
    }
    return if (PartAuthority.isLocalUri(uri)) {
      createForAttachmentUri(uri)
    } else {
      UriMediaInput(context, uri)
    }
  }

  private fun createForAttachmentUri(uri: Uri): MediaInput {
    val partId = PartUriParser(uri).partId
    if (!partId.isValid) {
      throw AssertionError()
    }
    val mediaDataSource = attachments.mediaDataSourceFor(partId, true) ?: throw AssertionError()
    return MediaDataSourceMediaInput(mediaDataSource)
  }
}
