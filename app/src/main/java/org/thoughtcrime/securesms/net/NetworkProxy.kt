/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.signal.core.util.net.resolveSystemProxy
import org.signal.libsignal.net.Network
import org.signal.network.config.SignalProxy
import org.thoughtcrime.securesms.BuildConfig
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

private const val TAG = "NetworkProxy"

sealed interface ProxyConfig {
  data object Direct : ProxyConfig

  data class ProxyAddress(
    val scheme: ProxyScheme,
    val host: String,
    val port: Int
  ) : ProxyConfig
}

/** Supported proxy schemes by [Network]. */
enum class ProxyScheme(val value: String) {
  TLS(Network.SIGNAL_TLS_PROXY_SCHEME),
  SOCKS("socks5"),
  HTTP("http")
}

fun SignalProxy.toProxyConfig() = ProxyConfig.ProxyAddress(ProxyScheme.TLS, host, port)

fun Proxy.toProxyConfig(): ProxyConfig? {
  val sa = address() as? InetSocketAddress ?: return null
  val scheme = when (type()) {
    Proxy.Type.HTTP -> ProxyScheme.HTTP
    Proxy.Type.SOCKS -> ProxyScheme.SOCKS
    Proxy.Type.DIRECT -> return ProxyConfig.Direct
  }
  return ProxyConfig.ProxyAddress(scheme, sa.hostString, sa.port)
}

/**
 * Resolves the appropriate proxy configuration or system defaults.
 * Falls back to direct connection if no valid proxy is found.
 *
 * @param signalProxy Explicit Signal TLS proxy setting, if configured
 * @return The resolved proxy configuration
 */
@WorkerThread
fun resolveProxyConfig(signalProxy: SignalProxy?): ProxyConfig {
  return signalProxy?.toProxyConfig()
    ?: resolveSystemProxy(targetUrl = BuildConfig.SIGNAL_URL)?.toProxyConfig()
    ?: ProxyConfig.Direct
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
        setProxy(config.scheme.value, config.host, config.port, null, null)
        Log.i(TAG, "Proxy configured: ${config.scheme}:${config.port}")
      } catch (e: IOException) {
        when (config.scheme) {
          ProxyScheme.TLS -> {
            Log.e(TAG, "Invalid Signal TLS proxy config! Failing connections until changed.", e)
            setInvalidProxy()
          }

          ProxyScheme.HTTP,
          ProxyScheme.SOCKS -> {
            Log.w(TAG, "Failed to configure ${config.scheme} proxy, falling back to direct connection.", e)
            clearProxy()
          }
        }
      }
    }
  }
}
