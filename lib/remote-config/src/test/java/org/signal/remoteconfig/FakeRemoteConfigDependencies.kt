/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.remoteconfig

import org.signal.network.api.RemoteConfigApi

/**
 * In-memory [RemoteConfigDependencies] for tests that don't need the network.
 */
class FakeRemoteConfigDependencies(
  override val isStagingBuild: Boolean = false,
  override val isInternalBuild: Boolean = false
) : RemoteConfigDependencies {

  override var currentConfig: String? = null
  override var lastFetchTime: Long = 0
  override var localOverrides: Map<String, String> = emptyMap()
  var refreshCount: Int = 0

  override var pendingConfig: String? = null
    get() = field ?: currentConfig

  override val remoteConfigApi: RemoteConfigApi
    get() = error("Not supported")

  override fun enqueueRefresh() {
    refreshCount++
  }

  override fun onBackgroundMessageProcessIntervalChanged() = Unit

  override fun onSvr3MigrationPhaseChanged(change: RemoteConfig.ConfigChange) = Unit
}
