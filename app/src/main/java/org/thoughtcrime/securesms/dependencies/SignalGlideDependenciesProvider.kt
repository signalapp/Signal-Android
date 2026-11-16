/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.net.Uri
import org.signal.glide.SignalGlideDependencies
import org.signal.glide.common.io.InputStreamFactory
import org.thoughtcrime.securesms.glide.DecryptableStreamFactory
import org.thoughtcrime.securesms.mms.PartAuthority

object SignalGlideDependenciesProvider : SignalGlideDependencies.Provider {
  override fun getUriInputStreamFactory(uri: Uri, thumbnailTimeUs: Long, contentType: String?): InputStreamFactory {
    return DecryptableStreamFactory(uri, thumbnailTimeUs, contentType)
  }

  override fun isLocalUri(uri: Uri): Boolean {
    return PartAuthority.isLocalUri(uri)
  }
}
