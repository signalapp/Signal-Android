/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.DismissibleBannerProducer
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.PowerManagerCompat
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences

class DozeBanner(private val context: Context, val dismissed: Boolean, private val onDismiss: () -> Unit) : Banner() {
  override val enabled: Boolean = !dismissed &&
    Build.VERSION.SDK_INT >= 23 && !SignalStore.account.fcmEnabled && !TextSecurePreferences.hasPromptedOptimizeDoze(context) && !ServiceUtil.getPowerManager(context).isIgnoringBatteryOptimizations(context.packageName)

  @Composable
  override fun DisplayBanner() {
    if (Build.VERSION.SDK_INT < 23) {
      throw IllegalStateException("Showing a Doze banner for an OS prior to Android 6.0")
    }
    DefaultBanner(
      title = stringResource(id = R.string.DozeReminder_optimize_for_missing_play_services),
      body = stringResource(id = R.string.DozeReminder_this_device_does_not_support_play_services_tap_to_disable_system_battery),
      actions = listOf(
        Action(android.R.string.ok) {
          TextSecurePreferences.setPromptedOptimizeDoze(context, true)
          PowerManagerCompat.requestIgnoreBatteryOptimizations(context)
        }
      ),
      onDismissListener = {
        TextSecurePreferences.setPromptedOptimizeDoze(context, true)
        onDismiss()
      }
    )
  }

  private class Producer(private val context: Context) : DismissibleBannerProducer<DozeBanner>(bannerProducer = {
    DozeBanner(context = context, dismissed = false, onDismiss = it)
  }) {
    override fun createDismissedBanner(): DozeBanner {
      return DozeBanner(context, true) {}
    }
  }

  companion object {
    @JvmStatic
    fun createFlow(context: Context): Flow<DozeBanner> {
      return Producer(context).flow
    }
  }
}
