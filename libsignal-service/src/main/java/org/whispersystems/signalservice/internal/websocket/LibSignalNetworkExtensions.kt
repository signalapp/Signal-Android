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
  val libsignalRemoteConfig: Map<String, String> = remoteConfig
    .filterKeys { it.startsWith("android.libsignal.") }
    .mapKeys { (k, _) -> k.removePrefix("android.libsignal.") }
    // libsignal-net's RemoteConfig diverges from JSON-spec by not quoting string values.
    .mapValues { (_, v) -> (v as? String) ?: JsonUtil.toJson(v) }

  this.setRemoteConfig(libsignalRemoteConfig)
}

/**
 * Helper method to apply settings from the SignalServiceConfiguration.
 */
fun Network.applyConfiguration(config: SignalServiceConfiguration) {
  val signalProxy = config.signalProxy.orNull()
  val systemHttpProxy = config.systemHttpProxy.orNull()

  when {
    (signalProxy != null) -> {
      try {
        this.setProxy(signalProxy.host, signalProxy.port)
      } catch (e: IOException) {
        Log.e(TAG, "Invalid proxy configuration set! Failing connections until changed.")
        this.setInvalidProxy()
      }
    }
    (systemHttpProxy != null) -> {
      try {
        this.setProxy("http", systemHttpProxy.host, systemHttpProxy.port, "", "")
      } catch (e: IOException) {
        // The Android settings screen where this is set explicitly calls out that apps are allowed to
        //  ignore the HTTP Proxy setting, so if using the specified proxy would cause us to break, let's
        //  try just ignoring it and seeing if that still lets us connect.
        Log.w(TAG, "Failed to set system HTTP proxy, ignoring and continuing...")
      }
    }
  }

  this.setCensorshipCircumventionEnabled(config.censored)
}
