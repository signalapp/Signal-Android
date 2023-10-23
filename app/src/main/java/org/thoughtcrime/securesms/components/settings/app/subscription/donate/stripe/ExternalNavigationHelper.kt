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
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.R

/**
 * Encapsulates the logic for navigating a user to a deeplink from within a webview or parsing out the fallback
 * or play store parameters to launch them into the market.
 */
object ExternalNavigationHelper {

  fun maybeLaunchExternalNavigationIntent(context: Context, webRequestUri: Uri?, launchIntent: (Intent) -> Unit): Boolean {
    val url = webRequestUri ?: return false
    if (url.scheme?.startsWith("http") == true || url.scheme == StripeApi.RETURN_URL_SCHEME) {
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
    val intent = Intent(Intent.ACTION_VIEW, url)
    try {
      launchIntent(intent)
    } catch (e: ActivityNotFoundException) {
      // Parses intent:// schema uris according to https://developer.chrome.com/docs/multidevice/android/intents/

      if (url.scheme?.equals("intent") == true) {
        val fragmentParts: Map<String, String?> = url.fragment
          ?.split(";")
          ?.associate {
            val parts = it.split('=', limit = 2)

            if (parts.size > 1) {
              parts[0] to parts[1]
            } else {
              parts[0] to null
            }
          } ?: emptyMap()

        val fallbackUri = fragmentParts["S.browser_fallback_url"]?.let { Uri.parse(it) }

        val packageId: String? = if (looksLikeAMarketLink(fallbackUri)) {
          fallbackUri!!.getQueryParameter("id")
        } else {
          fragmentParts["package"]
        }

        if (!packageId.isNullOrBlank()) {
          try {
            launchIntent(
              Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageId")
              )
            )
          } catch (e: ActivityNotFoundException) {
            toastOnActivityNotFound(context)
          }
        } else if (fallbackUri != null) {
          try {
            launchIntent(
              Intent(
                Intent.ACTION_VIEW,
                fallbackUri
              )
            )
          } catch (e: ActivityNotFoundException) {
            toastOnActivityNotFound(context)
          }
        }
      }
    }
  }

  private fun toastOnActivityNotFound(context: Context) {
    Toast.makeText(context, R.string.CommunicationActions_no_browser_found, Toast.LENGTH_SHORT).show()
  }

  private fun looksLikeAMarketLink(uri: Uri?): Boolean {
    return uri != null && uri.host == "play.google.com" && uri.getQueryParameter("id") != null
  }
}
