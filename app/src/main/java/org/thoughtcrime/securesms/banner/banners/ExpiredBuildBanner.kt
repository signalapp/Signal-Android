/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.PlayStoreUtil

/**
 * Banner to let the user know their build is about to expire.
 *
 * This serves as an example for how we can replicate the functionality of the old [org.thoughtcrime.securesms.components.reminder.Reminder] system purely in the new [Banner] system.
 */
class ExpiredBuildBanner(val context: Context) : Banner() {

  override var enabled = true

  @Composable
  override fun DisplayBanner() {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today),
      importance = Importance.TERMINAL,
      isDismissible = false,
      actions = listOf(
        Action(R.string.ExpiredBuildReminder_update_now) {
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
        }
      ),
      onHideListener = {},
      onDismissListener = {}
    )
  }

  companion object {
    @JvmStatic
    fun createFlow(context: Context): Flow<Banner> = createAndEmit {
      if (SignalStore.misc.isClientDeprecated) {
        ExpiredBuildBanner(context)
      } else {
        null
      }
    }
  }
}
