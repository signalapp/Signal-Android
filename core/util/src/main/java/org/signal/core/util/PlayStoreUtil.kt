/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.signal.core.util.logging.Log

object PlayStoreUtil {

  private val TAG = Log.tag(PlayStoreUtil::class)

  const val PLAY_STORE_PACKAGE = "com.android.vending"

  /** Opens the system app info screen for the Play Store, where the user can enable it. */
  @JvmStatic
  fun openPlayStoreSettings(context: Context) {
    try {
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PLAY_STORE_PACKAGE, null)))
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "Unable to open the Play Store's app settings", e)
    } catch (e: SecurityException) {
      Log.w(TAG, "Unable to open the Play Store's app settings", e)
    }
  }
}
