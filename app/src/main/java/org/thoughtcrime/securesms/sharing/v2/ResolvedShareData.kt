package org.thoughtcrime.securesms.sharing.v2

import android.net.Uri
import org.thoughtcrime.securesms.sharing.MultiShareArgs
import java.lang.UnsupportedOperationException

sealed class ResolvedShareData {

  abstract fun toMultiShareArgs(): MultiShareArgs

  data class Primitive(val text: CharSequence) : ResolvedShareData() {
    override fun toMultiShareArgs(): MultiShareArgs {
      return MultiShareArgs.Builder(setOf()).withDraftText(text.toString()).build()
    }
  }

  data class ExternalUri(
    val uri: Uri,
    val mimeType: String,
    val text: CharSequence?
  ) : ResolvedShareData() {
    override fun toMultiShareArgs(): MultiShareArgs {
      return MultiShareArgs.Builder(setOf()).withDataUri(uri).withDataType(mimeType).withDraftText(text?.toString()).build()
    }
  }

  data class Media(
    val media: List<org.thoughtcrime.securesms.mediasend.Media>
  ) : ResolvedShareData() {
    override fun toMultiShareArgs(): MultiShareArgs {
      return MultiShareArgs.Builder(setOf()).withMedia(media).build()
    }
  }

  object Failure : ResolvedShareData() {
    override fun toMultiShareArgs(): MultiShareArgs = throw UnsupportedOperationException()
  }
}
