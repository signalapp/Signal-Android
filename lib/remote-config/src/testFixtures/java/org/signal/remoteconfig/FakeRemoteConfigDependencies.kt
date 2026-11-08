/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.remoteconfig

import org.signal.network.api.RemoteConfigApi
import org.signal.storage.keyvalue.KeyValueDataSet
import org.signal.storage.keyvalue.KeyValueStore
import org.signal.storage.keyvalue.MockKeyValuePersistentStorage
import org.signal.storage.keyvalue.RemoteConfigValues

/**
 * In-memory [RemoteConfigDependencies] for tests that don't need the network.
 */
class FakeRemoteConfigDependencies(
  override val isStagingBuild: Boolean = false,
  override val isInternalBuild: Boolean = false
) : RemoteConfigDependencies {

  override val remoteConfigValues: RemoteConfigValues = RemoteConfigValues(KeyValueStore(MockKeyValuePersistentStorage.withDataSet(KeyValueDataSet())) { it.run() })
  var refreshCount: Int = 0

  override val remoteConfigApi: RemoteConfigApi
    get() = error("Not supported")

  override fun enqueueRefresh() {
    refreshCount++
  }

  override fun onBackgroundMessageProcessIntervalChanged() = Unit

  override fun onSvr3MigrationPhaseChanged(change: RemoteConfig.ConfigChange) = Unit
}
