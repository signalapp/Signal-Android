/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.Util
import kotlin.time.Duration.Companion.milliseconds

/**
 * Banner to let the user know their build is about to expire or has expired.
 */
class OutdatedBuildBanner(val context: Context, private val daysUntilExpiry: Int) : Banner() {

  override val enabled = SignalStore.misc.isClientDeprecated || daysUntilExpiry <= MAX_DAYS_UNTIL_EXPIRE

  @Composable
  override fun DisplayBanner() {
    DefaultBanner(
      title = null,
      body = if (SignalStore.misc.isClientDeprecated) {
        stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
      } else if (daysUntilExpiry == 0) {
        stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
      } else {
        pluralStringResource(id = R.plurals.OutdatedBuildReminder_your_version_of_signal_will_expire_in_n_days, count = daysUntilExpiry, daysUntilExpiry)
      },
      importance = if (SignalStore.misc.isClientDeprecated) {
        Importance.ERROR
      } else {
        Importance.NORMAL
      },
      actions = listOf(
        Action(R.string.ExpiredBuildReminder_update_now) {
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
        }
      )
    )
  }

  companion object {
    private const val MAX_DAYS_UNTIL_EXPIRE = 10

    @JvmStatic
    fun createFlow(context: Context): Flow<OutdatedBuildBanner> = createAndEmit {
      val daysUntilExpiry = Util.getTimeUntilBuildExpiry(SignalStore.misc.estimatedServerTime).milliseconds.inWholeDays.toInt()
      OutdatedBuildBanner(context, daysUntilExpiry)
    }
  }
}
