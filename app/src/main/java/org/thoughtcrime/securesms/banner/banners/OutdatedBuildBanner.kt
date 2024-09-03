/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
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
 *
 * @param status can be used to filter which conditions are shown.
 */
class OutdatedBuildBanner(val context: Context, private val daysUntilExpiry: Int, private val status: ExpiryStatus) : Banner() {

  override val enabled = when (status) {
    ExpiryStatus.OUTDATED_ONLY -> SignalStore.misc.isClientDeprecated
    ExpiryStatus.EXPIRED_ONLY -> daysUntilExpiry <= MAX_DAYS_UNTIL_EXPIRE
    ExpiryStatus.OUTDATED_OR_EXPIRED -> SignalStore.misc.isClientDeprecated || daysUntilExpiry <= MAX_DAYS_UNTIL_EXPIRE
  }

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    val bodyText = when (status) {
      ExpiryStatus.OUTDATED_ONLY -> if (daysUntilExpiry == 0) {
        stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
      } else {
        pluralStringResource(id = R.plurals.OutdatedBuildReminder_your_version_of_signal_will_expire_in_n_days, count = daysUntilExpiry, daysUntilExpiry)
      }

      ExpiryStatus.EXPIRED_ONLY -> stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
      ExpiryStatus.OUTDATED_OR_EXPIRED -> if (SignalStore.misc.isClientDeprecated) {
        stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
      } else if (daysUntilExpiry == 0) {
        stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today)
      } else {
        pluralStringResource(id = R.plurals.OutdatedBuildReminder_your_version_of_signal_will_expire_in_n_days, count = daysUntilExpiry, daysUntilExpiry)
      }
    }
    DefaultBanner(
      title = null,
      body = bodyText,
      importance = if (SignalStore.misc.isClientDeprecated) {
        Importance.ERROR
      } else {
        Importance.NORMAL
      },
      actions = listOf(
        Action(R.string.ExpiredBuildReminder_update_now) {
          PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(context)
        }
      ),
      paddingValues = contentPadding
    )
  }

  /**
   * A enumeration for [OutdatedBuildBanner] to limit it to showing either [OUTDATED_ONLY] status, [EXPIRED_ONLY] status, or both.
   *
   * [OUTDATED_ONLY] refers to builds that are still valid but need to be updated.
   * [EXPIRED_ONLY] refers to builds that are no longer allowed to connect to the service.
   */
  enum class ExpiryStatus {
    OUTDATED_ONLY,
    EXPIRED_ONLY,
    OUTDATED_OR_EXPIRED
  }

  companion object {
    private const val MAX_DAYS_UNTIL_EXPIRE = 10

    @JvmStatic
    fun createFlow(context: Context, status: ExpiryStatus): Flow<OutdatedBuildBanner> = createAndEmit {
      val daysUntilExpiry = Util.getTimeUntilBuildExpiry(SignalStore.misc.estimatedServerTime).milliseconds.inWholeDays.toInt()
      OutdatedBuildBanner(context, daysUntilExpiry, status)
    }
  }
}
