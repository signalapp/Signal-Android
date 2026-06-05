/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.verify

import android.content.Context
import android.content.Intent
import androidx.annotation.UiContext
import org.signal.core.util.Util
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.verify.SafetyNumberQrView.Companion.getSegments
import org.thoughtcrime.securesms.dependencies.AppDependencies

object VerifyDisplayRepository {
  fun writeToClipboard(fingerprint: Fingerprint) {
    Util.writeTextToClipboard(AppDependencies.application, "Safety numbers", getFormattedSafetyNumbers(fingerprint))
  }

  fun readFromClipboard(): String? {
    return Util.readTextFromClipboard(AppDependencies.application)
  }

  @UiContext
  fun createShareIntent(context: Context, fingerprint: Fingerprint): Intent {
    val shareString = """
        ${context.getString(R.string.VerifyIdentityActivity_our_signal_safety_number)}
        ${getFormattedSafetyNumbers(fingerprint)}
        
    """.trimIndent()
    return Intent().apply {
      action = Intent.ACTION_SEND
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, shareString)
    }
  }

  private fun getFormattedSafetyNumbers(fingerprint: Fingerprint): String {
    val segments = getSegments(fingerprint)
    val result = StringBuilder()
    for (i in segments.indices) {
      result.append(segments[i])
      if (i != segments.size - 1) {
        if ((i + 1) % 4 == 0) result.append('\n') else result.append(' ')
      }
    }
    return result.toString()
  }
}
