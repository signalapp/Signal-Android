/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.net.Uri
import org.signal.glide.SignalGlideDependencies
import org.signal.glide.common.io.InputStreamFactory
import org.thoughtcrime.securesms.glide.DecryptableStreamFactory

object SignalGlideDependenciesProvider : SignalGlideDependencies.Provider {
  override fun getUriInputStreamFactory(uri: Uri): InputStreamFactory {
    return DecryptableStreamFactory(uri)
  }
}
