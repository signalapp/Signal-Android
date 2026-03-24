/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import android.app.Application

object CoreUtilDependencies {

  private lateinit var _application: Application
  private lateinit var _provider: Provider
  private lateinit var _buildInfo: BuildInfo

  fun init(application: Application, provider: Provider, buildInfo: BuildInfo) {
    if (this::_provider.isInitialized) {
      return
    }

    _application = application
    _provider = provider
    _buildInfo = buildInfo
  }

  val application: Application
    get() = _application

  val buildInfo: BuildInfo
    get() = _buildInfo

  val isClientDeprecated: Boolean
    get() = _provider.provideIsClientDeprecated()

  fun getTimeUntilRemoteDeprecation(currentTime: Long): Long {
    return _provider.provideTimeUntilRemoteDeprecation(currentTime)
  }

  data class BuildInfo(
    val canonicalVersionCode: Int,
    val buildTimestamp: Long
  )

  interface Provider {
    fun provideIsClientDeprecated(): Boolean
    fun provideTimeUntilRemoteDeprecation(currentTime: Long): Long
  }
}
