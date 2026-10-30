/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.remoteconfig

import org.signal.network.api.RemoteConfigApi

/**
 * Injection point for application state and behavior that [RemoteConfig] reads and drives but does not own.
 */
interface RemoteConfigDependencies {
  /** Whether this build talks to the staging service. Some configs default to on for staging. */
  val isStagingBuild: Boolean

  /** Whether this build should always be treated as an internal user. */
  val isInternalBuild: Boolean

  /** The serialized config in use for this app session. */
  var currentConfig: String?

  /** The serialized config that will become [currentConfig] on the next app start. */
  var pendingConfig: String?

  var lastFetchTime: Long

  /** Persisted internal-user overrides, keyed by config key. */
  val localOverrides: Map<String, String>

  val remoteConfigApi: RemoteConfigApi

  /** Schedules a refresh of the config from the service in the background. */
  fun enqueueRefresh()

  fun onBackgroundMessageProcessIntervalChanged()

  fun onSvr3MigrationPhaseChanged(change: RemoteConfig.ConfigChange)
}
