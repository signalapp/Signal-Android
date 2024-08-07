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
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.util.TextSecurePreferences

class ServiceOutageBanner(context: Context) : Banner() {

  override val enabled = TextSecurePreferences.getServiceOutage(context)

  @Composable
  override fun DisplayBanner() {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.reminder_header_service_outage_text),
      importance = Importance.ERROR
    )
  }

  companion object {

    @JvmStatic
    fun createFlow(context: Context): Flow<ServiceOutageBanner> = createAndEmit {
      ServiceOutageBanner(context)
    }
  }
}
