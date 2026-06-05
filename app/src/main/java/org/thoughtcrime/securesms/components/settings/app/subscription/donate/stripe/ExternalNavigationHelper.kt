/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.R
import java.net.URISyntaxException

/**
 * Encapsulates the logic for navigating a user to a deeplink from within a webview or parsing out the fallback
 * or play store parameters to launch them into the market.
 */
object ExternalNavigationHelper {

  private val TAG = Log.tag(ExternalNavigationHelper::class)

  fun maybeLaunchExternalNavigationIntent(context: Context, webRequestUri: Uri?, force: Boolean = false, launchIntent: (Intent) -> Unit): Boolean {
    val url = webRequestUri ?: return false
    if (!force && (url.scheme?.startsWith("http") == true || url.scheme == StripeApi.RETURN_URL_SCHEME)) {
      return false
    }

    val intent = try {
      Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).sanitizeWebIntent()
    } catch (e: URISyntaxException) {
      Log.w(TAG, "Failed to parse web intent URI.", e)
      return false
    }

    val targetLabel = resolveTargetLabel(context, intent)
    val message = if (targetLabel != null) {
      context.getString(R.string.ExternalNavigationHelper__once_payment_confirmed_in_app, targetLabel)
    } else {
      context.getString(R.string.ExternalNavigationHelper__once_this_payment_is_confirmed)
    }

    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.ExternalNavigationHelper__leave_signal_to_confirm_payment)
      .setMessage(message)
      .setPositiveButton(android.R.string.ok) { _, _ -> attemptIntentLaunch(context, intent, launchIntent) }
      .setNegativeButton(android.R.string.cancel, null)
      .show()

    return true
  }

  private fun attemptIntentLaunch(context: Context, intent: Intent, launchIntent: (Intent) -> Unit) {
    try {
      launchIntent(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Cannot find activity for intent. Checking for fallback URL.", e)

      val fallback = intent.getStringExtra("browser_fallback_url")
      if (fallback.isNotNullOrBlank()) {
        try {
          launchIntent(Intent.parseUri(fallback, Intent.URI_INTENT_SCHEME).sanitizeWebIntent())
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "Failed to launch fallback URL.", e)
          toastOnActivityNotFound(context)
        }
      }
    }
  }

  private fun resolveTargetLabel(context: Context, intent: Intent): CharSequence? {
    val resolveInfo = context.packageManager.resolveActivity(intent, 0) ?: return null
    return resolveInfo.loadLabel(context.packageManager).toString().takeIf { it.isNotBlank() }
  }

  /**
   * Sanitize an intent parsed from a web-originated URI to prevent targeting
   * non-exported or internal activities. This mirrors the sanitization that
   * browsers apply to intent:// URIs before dispatching them.
   */
  @VisibleForTesting
  fun Intent.sanitizeWebIntent(): Intent {
    component = null
    selector = null
    addCategory(Intent.CATEGORY_BROWSABLE)
    flags = flags and (
      Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
      ).inv()
    return this
  }

  private fun toastOnActivityNotFound(context: Context) {
    Toast.makeText(context, R.string.CommunicationActions_no_browser_found, Toast.LENGTH_SHORT).show()
  }
}
