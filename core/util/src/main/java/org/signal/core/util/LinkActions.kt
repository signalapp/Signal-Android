/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import org.signal.core.util.logging.Log

object LinkActions {
  private val TAG = Log.tag(LinkActions::class)

  @JvmStatic
  fun openUrl(context: Context, url: String, onError: (OpenUrlError) -> Unit) {
    try {
      // FLAG_ACTIVITY_NEW_TASK is required when starting from a non-Activity context, but it is
      // wanted in every case: without it the target app's activity is pushed onto our own task, so
      // Recents shows a single card with the Signal icon that actually contains the other app.
      // Giving the link its own task also means an already-running instance of the target app is
      // brought forward rather than duplicated inside our stack.
      val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
      Log.w(TAG, "Unable to open URL: no browser activity found")
      onError(OpenUrlError.NoBrowserFound)
    }
  }

  sealed interface OpenUrlError {
    data object NoBrowserFound : OpenUrlError
  }
}
