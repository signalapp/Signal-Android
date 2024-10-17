/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.logging.Log

/**
 * A class that can be instantiated with a list of [Flow]s that produce [Banner]s, then applied to a [ComposeView], typically within a [Fragment].
 * Usually, the [Flow]s will come from [Banner.BannerFactory] instances, but may also be produced by the other properties of the host.
 */
class BannerManager @JvmOverloads constructor(
  private val banners: List<Banner<*>>,
  private val onNewBannerShownListener: () -> Unit = {},
  private val onNoBannerShownListener: () -> Unit = {}
) {

  companion object {
    val TAG = Log.tag(BannerManager::class)
  }

  /**
   * Re-evaluates the [Banner]s, choosing one to render (if any) and updating the view.
   */
  fun updateContent(composeView: ComposeView) {
    val banner: Banner<Any>? = banners.firstOrNull { it.enabled } as Banner<Any>?

    composeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        if (banner == null) {
          onNoBannerShownListener()
          return@setContent
        }

        val state: State<Any?> = banner.dataFlow.collectAsStateWithLifecycle(initialValue = null)
        val bannerState by state

        bannerState?.let { model ->
          SignalTheme {
            Box {
              banner.DisplayBanner(model, PaddingValues(horizontal = 12.dp, vertical = 8.dp))
            }
          }

          onNewBannerShownListener()
        } ?: onNoBannerShownListener()
      }
    }
  }
}
