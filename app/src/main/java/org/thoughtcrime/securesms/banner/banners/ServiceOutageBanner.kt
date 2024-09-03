/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.banners

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.util.TextSecurePreferences

class ServiceOutageBanner(outageInProgress: Boolean) : Banner() {

  constructor(context: Context) : this(TextSecurePreferences.getServiceOutage(context))

  override val enabled = outageInProgress

  @Composable
  override fun DisplayBanner(contentPadding: PaddingValues) {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.reminder_header_service_outage_text),
      importance = Importance.ERROR,
      paddingValues = contentPadding
    )
  }

  /**
   * A class that can be held by a listener but still produce new [ServiceOutageBanner] in its flow.
   * Designed for being called upon by a listener that is listening to changes in [TextSecurePreferences]
   */
  class Producer(private val context: Context) {
    private val _flow = MutableSharedFlow<Boolean>(replay = 1)
    val flow: Flow<ServiceOutageBanner> = _flow.map { ServiceOutageBanner(context) }

    init {
      queryAndEmit()
    }

    fun queryAndEmit() {
      _flow.tryEmit(TextSecurePreferences.getServiceOutage(context))
    }
  }

  companion object {
    private val TAG = Log.tag(ServiceOutageBanner::class)
  }
}
