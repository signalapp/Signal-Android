/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:JvmName("LibSignalNetworkExtensions")

package org.whispersystems.signalservice.internal.websocket

import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.net.Network
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.io.IOException

private const val TAG = "LibSignalNetworkExtensions"

fun Network.transformAndSetRemoteConfig(remoteConfig: Map<String, Any>) {
  val libsignalRemoteConfig = HashMap<String, String>()
  for (key in remoteConfig.keys) {
    if (key.startsWith("android.libsignal.") or key.startsWith("global.libsignal.")) {
      libsignalRemoteConfig[key] = JsonUtil.toJson(remoteConfig[key])
    }
  }

  this.setRemoteConfig(libsignalRemoteConfig)
}

/**
 * Helper method to apply settings from the SignalServiceConfiguration.
 */
fun Network.applyConfiguration(config: SignalServiceConfiguration) {
  val proxy = config.signalProxy.orNull()

  if (proxy == null) {
    this.clearProxy()
  } else {
    try {
      this.setProxy(proxy.host, proxy.port)
    } catch (e: IOException) {
      Log.e(TAG, "Invalid proxy configuration set! Failing connections until changed.")
      this.setInvalidProxy()
    }
  }

  this.setCensorshipCircumventionEnabled(config.censored)
}
