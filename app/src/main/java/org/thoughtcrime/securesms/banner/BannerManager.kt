/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.compose.SignalTheme

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
    composeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val banner: Banner<Any>? = banners.firstOrNull { it.enabled } as Banner<Any>?
        if (banner == null) {
          onNoBannerShownListener()
          return@setContent
        }

        key(banner) {
          val bannerState by banner.dataFlow.collectAsStateWithLifecycle(initialValue = null)

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

  /**
   * Displays the current banner.
   */
  @Composable
  fun Banner() {
    val banner: Banner<Any>? = banners.firstOrNull { it.enabled } as Banner<Any>?
    if (banner == null) {
      return
    }

    key(banner) {
      val bannerState by banner.dataFlow.collectAsStateWithLifecycle(initialValue = null)

      bannerState?.let { model ->
        banner.DisplayBanner(model, PaddingValues(horizontal = 12.dp, vertical = 8.dp))
      }
    }
  }
}
