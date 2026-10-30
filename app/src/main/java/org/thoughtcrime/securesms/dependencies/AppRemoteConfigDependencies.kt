/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.signal.core.util.logging.Log
import org.signal.network.api.RemoteConfigApi
import org.signal.remoteconfig.RemoteConfig
import org.signal.remoteconfig.RemoteConfigDependencies
import org.thoughtcrime.securesms.jobs.RemoteConfigRefreshJob
import org.thoughtcrime.securesms.jobs.Svr3MirrorJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messageprocessingalarm.RoutineMessageFetchReceiver
import org.thoughtcrime.securesms.net.SignalNetwork
import org.thoughtcrime.securesms.util.Environment

/**
 * Supplies [RemoteConfig] with the app's storage and network.
 */
object AppRemoteConfigDependencies : RemoteConfigDependencies {

  private val TAG = Log.tag(AppRemoteConfigDependencies::class)

  override val isStagingBuild: Boolean = Environment.IS_STAGING

  override val isInternalBuild: Boolean
    get() = Environment.isInternal()

  override var currentConfig: String?
    get() = SignalStore.remoteConfig.currentConfig
    set(value) {
      SignalStore.remoteConfig.currentConfig = value
    }

  override var pendingConfig: String?
    get() = SignalStore.remoteConfig.pendingConfig
    set(value) {
      SignalStore.remoteConfig.pendingConfig = value
    }

  override var lastFetchTime: Long
    get() = SignalStore.remoteConfig.lastFetchTime
    set(value) {
      SignalStore.remoteConfig.lastFetchTime = value
    }

  override val localOverrides: Map<String, String>
    get() = SignalStore.internal.remoteConfigOverrides

  override val remoteConfigApi: RemoteConfigApi
    get() = SignalNetwork.remoteConfigApi

  override fun enqueueRefresh() {
    AppDependencies.jobManager.add(RemoteConfigRefreshJob())
  }

  override fun onBackgroundMessageProcessIntervalChanged() {
    RoutineMessageFetchReceiver.startOrUpdateAlarm(AppDependencies.application)
  }

  override fun onSvr3MigrationPhaseChanged(change: RemoteConfig.ConfigChange) {
    if ((change.oldValue == null || change.oldValue == 0) && change.newValue == 1) {
      Log.w(TAG, "Detected the SVR3 migration phase change to 1! Enqueuing a mirroring job.")
      AppDependencies.jobManager.add(Svr3MirrorJob())
    }
  }
}
