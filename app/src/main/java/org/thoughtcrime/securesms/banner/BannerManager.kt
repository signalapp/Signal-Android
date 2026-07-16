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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import org.signal.core.ui.compose.theme.SignalTheme
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

  private fun selectEnabledBanner(): Banner<Any>? = banners.firstOrNull { it.enabled } as Banner<Any>?

  /**
   * Reactively selects which [Banner] should be shown. Re-evaluates whenever any banner signals via
   * [Banner.stateUpdates] that its eligibility may have changed.
   */
  private val selectedBanner: Flow<Banner<Any>?> =
    if (banners.isEmpty()) {
      flowOf(null)
    } else {
      merge(*banners.map { it.stateUpdates }.toTypedArray())
        .onStart { emit(Unit) }
        .map { selectEnabledBanner() }
        .distinctUntilChanged()
    }

  /**
   * Re-evaluates the [Banner]s, choosing one to render (if any) and updating the view.
   */
  fun updateContent(composeView: ComposeView) {
    composeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val banner: Banner<Any>? by selectedBanner.collectAsStateWithLifecycle(initialValue = selectEnabledBanner())
        val selected = banner
        if (selected == null) {
          onNoBannerShownListener()
          return@setContent
        }

        key(selected) {
          val bannerState by selected.dataFlow.collectAsStateWithLifecycle(initialValue = null)

          bannerState?.let { model ->
            SignalTheme {
              Box {
                selected.DisplayBanner(model, PaddingValues(horizontal = 12.dp, vertical = 8.dp))
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
    val banner: Banner<Any>? by selectedBanner.collectAsStateWithLifecycle(initialValue = selectEnabledBanner())
    val selected = banner ?: return

    key(selected) {
      val bannerState by selected.dataFlow.collectAsStateWithLifecycle(initialValue = null)

      bannerState?.let { model ->
        selected.DisplayBanner(model, PaddingValues(horizontal = 12.dp, vertical = 8.dp))
      }
    }
  }
}
