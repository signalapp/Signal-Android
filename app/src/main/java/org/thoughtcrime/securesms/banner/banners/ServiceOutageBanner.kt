/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.util.TextSecurePreferences

class ServiceOutageBanner(outageInProgress: Boolean) : Banner() {

  constructor(context: Context) : this(TextSecurePreferences.getServiceOutage(context))

  override val enabled = outageInProgress

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
    fun createOneShotFlow(context: Context): Flow<ServiceOutageBanner> = createAndEmit {
      ServiceOutageBanner(context)
    }

    /**
     * Take a [Flow] of [Boolean] values representing the service status and map it into a [Flow] of [ServiceOutageBanner]
     */
    @JvmStatic
    fun fromFlow(statusFlow: Flow<Boolean>): Flow<ServiceOutageBanner> = statusFlow.map { ServiceOutageBanner(it) }
  }
}
