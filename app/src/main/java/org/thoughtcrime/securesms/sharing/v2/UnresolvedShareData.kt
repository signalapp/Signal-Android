package org.thoughtcrime.securesms.sharing.v2

import android.net.Uri

sealed class UnresolvedShareData {
  data class ExternalMultiShare(val uris: List<Uri>) : UnresolvedShareData()
  data class ExternalSingleShare(val uri: Uri, val mimeType: String?, val text: CharSequence?) : UnresolvedShareData()
  data class ExternalPrimitiveShare(val text: CharSequence) : UnresolvedShareData()
}
