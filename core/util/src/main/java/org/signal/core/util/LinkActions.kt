/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Activity
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
      val intent = Intent(Intent.ACTION_VIEW, url.toUri())
      if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
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
