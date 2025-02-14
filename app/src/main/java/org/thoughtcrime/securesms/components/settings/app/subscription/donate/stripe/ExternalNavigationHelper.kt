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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.R

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

    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.ExternalNavigationHelper__leave_signal_to_confirm_payment)
      .setMessage(R.string.ExternalNavigationHelper__once_this_payment_is_confirmed)
      .setPositiveButton(android.R.string.ok) { _, _ -> attemptIntentLaunch(context, url, launchIntent) }
      .setNegativeButton(android.R.string.cancel, null)
      .show()

    return true
  }

  private fun attemptIntentLaunch(context: Context, url: Uri, launchIntent: (Intent) -> Unit) {
    val intent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
    try {
      launchIntent(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Cannot find activity for intent. Checking for fallback URL.", e)

      val fallback = intent.getStringExtra("browser_fallback_url")
      if (fallback.isNotNullOrBlank()) {
        try {
          launchIntent(Intent.parseUri(fallback, Intent.URI_INTENT_SCHEME))
        } catch (e: ActivityNotFoundException) {
          Log.w(TAG, "Failed to launch fallback URL.", e)
          toastOnActivityNotFound(context)
        }
      }
    }
  }

  private fun toastOnActivityNotFound(context: Context) {
    Toast.makeText(context, R.string.CommunicationActions_no_browser_found, Toast.LENGTH_SHORT).show()
  }
}
