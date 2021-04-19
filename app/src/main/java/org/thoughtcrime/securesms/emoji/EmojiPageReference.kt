package org.thoughtcrime.securesms.emoji

import android.net.Uri
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader

/**
 * Used by Emoji provider to set up a glide request.
 */
class EmojiPageReference {

  val model: Any

  constructor(uri: Uri) {
    model = uri
  }

  constructor(decryptableUri: DecryptableStreamUriLoader.DecryptableUri) {
    model = decryptableUri
  }
}

typealias EmojiPageReferenceFactory = (uri: Uri) -> EmojiPageReference
