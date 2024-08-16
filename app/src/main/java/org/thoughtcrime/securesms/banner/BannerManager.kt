/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.signal.core.util.logging.Log

/**
 * A class that can be instantiated with a list of [Flow]s that produce [Banner]s, then applied to a [ComposeView], typically within a [Fragment].
 * Usually, the [Flow]s will come from [Banner.BannerFactory] instances, but may also be produced by the other properties of the host.
 */
class BannerManager @JvmOverloads constructor(
  allFlows: Iterable<Flow<Banner>>,
  private val onNewBannerShownListener: () -> Unit = {},
  private val onNoBannerShownListener: () -> Unit = {}
) {

  companion object {
    val TAG = Log.tag(BannerManager::class)
  }

  /**
   * Takes the flows and combines them into one so that a new [Flow] value from any of them will trigger an update to the UI.
   *
   * **NOTE**: This will **not** emit its first value until **all** of the input flows have each emitted *at least one value*.
   */
  private val combinedFlow: Flow<List<Banner>> = combine(allFlows) { banners: Array<Banner> ->
    banners.filter { it.enabled }.toList()
  }

  /**
   * Sets the content of the provided [ComposeView] to one that consumes the lists emitted by [combinedFlow] and displays them.
   */
  fun setContent(composeView: ComposeView) {
    composeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val state = combinedFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        val bannerToDisplay = state.value.firstOrNull()
        if (bannerToDisplay != null) {
          Box {
            bannerToDisplay.DisplayBanner()
          }

          onNewBannerShownListener()
        } else {
          onNoBannerShownListener()
        }
      }
    }
  }
}
