/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.signal.core.util.logging.Log

/**
 * This class represents a banner across the top of the screen.
 *
 * Typically, a class will subclass [Banner] and have a nested class that subclasses [BannerFactory].
 * The constructor for an implementation of [Banner] should be very lightweight, as it is may be called frequently.
 */
abstract class Banner {
  companion object {
    private val TAG = Log.tag(Banner::class)

    /**
     * A helper function to create a [Flow] of a [Banner].
     *
     * @param bannerFactory a block the produces a [Banner], or null. Returning null will complete the [Flow] without emitting any values.
     */
    @JvmStatic
    fun <T : Banner> createAndEmit(bannerFactory: () -> T): Flow<T> {
      return bannerFactory().let {
        flow { emit(it) }
      }
    }
  }

  /**
   * Whether or not the [Banner] should be shown (enabled) or hidden (disabled).
   */
  abstract val enabled: Boolean

  /**
   * Composable function to display content when [enabled] is true.
   *
   * @see [org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner]
   */
  @Composable
  abstract fun DisplayBanner()
}
