package org.thoughtcrime.securesms.media

import android.content.Context
import android.net.Uri
import androidx.annotation.RequiresApi
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.attachments
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.PartUriParser
import org.thoughtcrime.securesms.video.MediaDataSourceProvider
import org.thoughtcrime.securesms.video.interfaces.MediaInput
import org.thoughtcrime.securesms.video.interfaces.MediaInputFactory
import org.thoughtcrime.securesms.video.videoconverter.mediadatasource.MediaDataSourceMediaInput
import java.io.IOException

/**
 * A media input source that is decrypted on the fly.
 */
@RequiresApi(api = 23)
object DecryptableUriMediaInput : MediaInputFactory {
  @Throws(IOException::class)
  override fun createForUri(context: Context, uri: Uri): MediaInput {
    if (AppDependencies.blobs.isAuthority(uri)) {
      return MediaDataSourceMediaInput(MediaDataSourceProvider.getMediaDataSource(context, uri))
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
