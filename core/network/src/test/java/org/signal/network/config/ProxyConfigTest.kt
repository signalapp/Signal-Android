/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Optional

class ProxyConfigTest {

  companion object {
    private const val TARGET_URL = "https://chat.signal.org"
  }

  @Test
  fun `resolve returns Direct when there is no system proxy`() {
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(Proxy.NO_PROXY))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve returns Direct for a loopback (PAC) proxy by hostname`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("localhost", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve returns Direct for an unresolved loopback (PAC) proxy by ipv4`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve returns Direct for an unresolved loopback (PAC) proxy by ipv6`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("[::1]", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve returns Direct for a resolved loopback (PAC) proxy`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve maps a non-loopback numeric system proxy`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("10.0.0.1", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "10.0.0.1", 8080))
  }

  @Test
  fun `resolve maps a manual http system proxy`() {
    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(proxy))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "proxy.example.com", 8080))
  }

  @Test
  fun `resolve prefers an explicit signal proxy over the system proxy`() {
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false, signalProxy = SignalProxy("signal.tls.proxy", 443)), TARGET_URL, StubProxySelector(system))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.TLS, "signal.tls.proxy", 443))
  }

  @Test
  fun `resolve ignores the system proxy when the configuration is censored`() {
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(configuration(censored = true), TARGET_URL, StubProxySelector(system))
    assertThat(config).isEqualTo(ProxyConfig.Direct)
  }

  @Test
  fun `resolve uses the system proxy when the configuration is not censored`() {
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(configuration(censored = false), TARGET_URL, StubProxySelector(system))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.HTTP, "proxy.example.com", 8080))
  }

  @Test
  fun `resolve prefers an explicit signal proxy over a censored configuration`() {
    val system = Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080))
    val config = ProxyConfig.resolve(configuration(censored = true, signalProxy = SignalProxy("signal.tls.proxy", 443)), TARGET_URL, StubProxySelector(system))
    assertThat(config).isEqualTo(ProxyConfig.ProxyAddress(ProxyConfig.ProxyScheme.TLS, "signal.tls.proxy", 443))
  }

  private fun configuration(censored: Boolean, signalProxy: SignalProxy? = null): SignalServiceConfiguration {
    return SignalServiceConfiguration(
      signalServiceUrls = emptyArray(),
      signalCdnUrlMap = emptyMap(),
      signalStorageUrls = emptyArray(),
      signalCdsiUrls = emptyArray(),
      signalSvr2Urls = emptyArray(),
      networkInterceptors = emptyList(),
      dns = Optional.empty(),
      signalProxy = Optional.ofNullable(signalProxy),
      zkGroupServerPublicParams = ByteArray(0),
      genericServerPublicParams = ByteArray(0),
      backupServerPublicParams = ByteArray(0),
      censored = censored
    )
  }

  private class StubProxySelector(private vararg val proxies: Proxy) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> = proxies.toList()
    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
  }
}
