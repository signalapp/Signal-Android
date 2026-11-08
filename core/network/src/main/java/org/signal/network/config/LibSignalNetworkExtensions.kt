/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:JvmName("LibSignalNetworkExtensions")

package org.signal.network.config

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.Network
import java.io.IOException

private const val TAG = "LibSignalNetworkExtensions"

/**
 * Helper method to apply settings from the SignalServiceConfiguration.
 */
fun Network.applyConfiguration(config: SignalServiceConfiguration) {
  this.setCensorshipCircumventionEnabled(config.censored)
}

/**
 * Configures the [Network] instance with the given proxy settings.
 *
 * TLS Proxies: configuration errors mark the proxy as invalid, causing future connections to
 * fail until the proxy setting is changed. These are explicitly configured by the user in the app
 * and must be respected.
 *
 * System Proxies: the Android system settings screen explicitly calls out that apps are allowed
 * to ignore the proxy setting, so if configuration fails, we fall back to direct connection
 * rather than breaking connectivity.
 */
fun Network.configureProxy(config: ProxyConfig) {
  when (config) {
    ProxyConfig.Direct -> {
      Log.i(TAG, "No proxy configured.")
      clearProxy()
    }

    is ProxyConfig.ProxyAddress -> {
      try {
        when (config.scheme) {
          ProxyConfig.ProxyScheme.TLS -> setProxy(config.host, config.port)
          ProxyConfig.ProxyScheme.HTTP,
          ProxyConfig.ProxyScheme.SOCKS -> setProxy(config.scheme.value, config.host, config.port, null, null)
        }
        Log.i(TAG, "Proxy configured: ${config.scheme}/${config.port}")
      } catch (e: IOException) {
        when (config.scheme) {
          ProxyConfig.ProxyScheme.TLS -> {
            Log.e(TAG, "Invalid Signal TLS proxy config! Failing connections until changed.", e)
            setInvalidProxy()
          }

          ProxyConfig.ProxyScheme.HTTP,
          ProxyConfig.ProxyScheme.SOCKS -> {
            Log.w(TAG, "Failed to configure ${config.scheme} proxy, falling back to direct connection.", e)
            clearProxy()
          }
        }
      }
    }
  }
}
