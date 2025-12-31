/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [Dispatchers] wrapper to allow tests to inject test dispatchers.
 */
object SignalDispatchers {

  private var dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider

  fun setDispatcherProvider(dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider) {
    this.dispatcherProvider = dispatcherProvider
  }

  val Main get() = dispatcherProvider.main
  val IO get() = dispatcherProvider.io
  val Default get() = dispatcherProvider.default
  val Unconfined get() = dispatcherProvider.unconfined

  interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
  }

  private object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
  }
}
