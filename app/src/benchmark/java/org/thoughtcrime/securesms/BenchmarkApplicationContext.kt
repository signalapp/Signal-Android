/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.app.Application
import org.signal.benchmark.network.BenchmarkWebSocketConnection
import org.signal.libsignal.net.Network
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider
import org.whispersystems.signalservice.api.util.UptimeSleepTimer
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

class BenchmarkApplicationContext : ApplicationContext() {

  override fun initializeAppDependencies() {
    AppDependencies.init(this, BenchmarkDependencyProvider(this, ApplicationDependencyProvider(this)))
  }

  override fun beginJobLoop() = Unit

  fun beginJobLoopForTests() {
    super.beginJobLoop()
  }

  override fun onForeground() = Unit

  class BenchmarkDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {
    override fun provideAuthWebSocket(
      signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>,
      libSignalNetworkSupplier: Supplier<Network>
    ): SignalWebSocket.AuthenticatedWebSocket {
      return SignalWebSocket.AuthenticatedWebSocket(
        connectionFactory = { BenchmarkWebSocketConnection.create() },
        canConnect = { true },
        sleepTimer = UptimeSleepTimer(),
        disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
      )
    }
  }
}
