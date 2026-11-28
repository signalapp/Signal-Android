/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.config

import org.signal.core.util.logging.Log
import org.signal.libsignal.net.Network
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

sealed interface ProxyConfig {
  data object Direct : ProxyConfig

  data class ProxyAddress(
    val scheme: ProxyScheme,
    val host: String,
    val port: Int
  ) : ProxyConfig

  /** Supported proxy schemes by [Network]. */
  enum class ProxyScheme(val value: String) {
    TLS(Network.SIGNAL_TLS_PROXY_SCHEME),
    SOCKS("socks5"),
    HTTP("http")
  }

  companion object {
    private val TAG = Log.tag(ProxyConfig::class)

    @JvmStatic
    @JvmOverloads
    fun resolve(
      configuration: SignalServiceConfiguration,
      targetUrl: String,
      proxySelector: ProxySelector? = ProxySelector.getDefault()
    ): ProxyConfig {
      val signalProxy = configuration.signalProxy.orElse(null)
      return when {
        signalProxy != null -> signalProxy.toProxyConfig()
        configuration.censored -> Direct
        else -> resolveSystemProxy(targetUrl, proxySelector)?.let { fromSystemProxy(it) } ?: Direct
      }
    }

    /**
     * Resolves the system-configured [Proxy] for the given URL using [ProxySelector].
     *
     * @return null if no proxy is configured.
     */
    private fun resolveSystemProxy(targetUrl: String, proxySelector: ProxySelector?): Proxy? {
      return try {
        proxySelector?.select(URI.create(targetUrl))?.firstOrNull()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve the system proxy, falling back to direct.", e)
        null
      }
    }

    private fun fromSystemProxy(proxy: Proxy): ProxyConfig? {
      val scheme = when (proxy.type()) {
        Proxy.Type.HTTP -> ProxyScheme.HTTP
        Proxy.Type.SOCKS -> ProxyScheme.SOCKS
        Proxy.Type.DIRECT -> return null
      }

      val socketAddress = proxy.address() as? InetSocketAddress ?: return null

      // Android surfaces a PAC (auto-config) proxy as a loopback bridge. We do not support PAC, so skip it and connect directly.
      if (socketAddress.isLoopback()) {
        return null
      }

      return ProxyAddress(scheme, socketAddress.hostString, socketAddress.port)
    }

    /** [ProxySelector] returns unresolved addresses, so [InetSocketAddress.getAddress] is normally null and only the host string is available. */
    private fun InetSocketAddress.isLoopback(): Boolean {
      address?.let { return it.isLoopbackAddress }

      val host = hostString.removeSurrounding("[", "]")
      return host.equals("localhost", ignoreCase = true) || host.startsWith("127.") || host == "::1" || host == "0:0:0:0:0:0:0:1"
    }
  }
}
