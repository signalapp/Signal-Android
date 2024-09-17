/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

/**
 * This class represents a banner across the top of the screen.
 *
 * Banners are submitted to a [BannerManager], which will render the first [enabled] Banner in it's list.
 * After a Banner is selected, the [BannerManager] will listen to the [dataFlow] and use the emitted [Model]s to render the [DisplayBanner] composable.
 */
abstract class Banner<Model> {

  /**
   * Whether or not the [Banner] is eligible for display. This is read on the main thread and therefore should be very fast.
   */
  abstract val enabled: Boolean

  /**
   * A [Flow] that emits the model to be displayed in the [DisplayBanner] composable.
   * This flow will only be subscribed to if the banner is [enabled].
   */
  abstract val dataFlow: Flow<Model>

  /**
   * Composable function to display the content emitted from [dataFlow].
   * You likely want to use [org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner].
   */
  @Composable
  abstract fun DisplayBanner(model: Model, contentPadding: PaddingValues)
}
