/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.glide

import android.net.Uri
import org.signal.core.util.logging.Log
import org.signal.glide.common.io.InputStreamFactory
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.io.InputStream

/**
 * A factory that creates a new [InputStream] for the given [Uri] each time [create] is called.
 */
class DecryptableStreamFactory(
  private val uri: Uri
) : InputStreamFactory {
  companion object {
    private val TAG = Log.tag(DecryptableStreamFactory::class)
  }

  override fun create(): InputStream {
    return try {
      DecryptableStreamLocalUriFetcher(AppDependencies.application, uri).loadResource(uri, AppDependencies.application.contentResolver)
    } catch (e: Exception) {
      Log.w(TAG, "Error creating input stream for URI.", e)
      throw e
    }
  }
}
