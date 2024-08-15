/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

abstract class DismissibleBannerProducer<T : Banner>(bannerProducer: (dismissListener: () -> Unit) -> T) {
  abstract fun createDismissedBanner(): T

  private val mutableSharedFlow: MutableSharedFlow<T> = MutableSharedFlow(replay = 1)
  private val dismissListener = {
    mutableSharedFlow.tryEmit(createDismissedBanner())
  }

  init {
    mutableSharedFlow.tryEmit(bannerProducer(dismissListener))
  }

  val flow: Flow<T> = mutableSharedFlow
}
