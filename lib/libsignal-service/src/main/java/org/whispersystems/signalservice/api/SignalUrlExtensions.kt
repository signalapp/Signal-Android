/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.api.util.Tls12SocketFactory
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalUrl
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager
import org.whispersystems.signalservice.internal.util.Util
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Select a a URL at random to use.
 */
fun <T : SignalUrl> Array<T>.chooseUrl(): T {
  return this[(Math.random() * size).toInt()]
}

/**
 * Build and configure an [OkHttpClient] as defined by the target [SignalUrl] and provided [configuration].
 */
fun <T : SignalUrl> T.buildOkHttpClient(configuration: SignalServiceConfiguration): OkHttpClient {
  val (socketFactory, trustManager) = createTlsSocketFactory(this.trustStore)

  val builder = OkHttpClient.Builder()
    .sslSocketFactory(socketFactory, trustManager)
    .connectionSpecs(this.connectionSpecs.orElse(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
    .retryOnConnectionFailure(false)
    .readTimeout(30, TimeUnit.SECONDS)
    .connectTimeout(30, TimeUnit.SECONDS)

  for (interceptor in configuration.networkInterceptors) {
    builder.addInterceptor(interceptor)
  }

  if (configuration.signalProxy.isPresent) {
    val proxy = configuration.signalProxy.get()
    builder.socketFactory(TlsProxySocketFactory(proxy.host, proxy.port, configuration.dns))
  }

  return builder.build()
}

private fun createTlsSocketFactory(trustStore: TrustStore): Pair<SSLSocketFactory, X509TrustManager> {
  return try {
    val context = SSLContext.getInstance("TLS")
    val trustManagers = BlacklistingTrustManager.createFor(trustStore)
    context.init(null, trustManagers, null)
    Tls12SocketFactory(context.socketFactory) to trustManagers[0] as X509TrustManager
  } catch (e: NoSuchAlgorithmException) {
    throw AssertionError(e)
  } catch (e: KeyManagementException) {
    throw AssertionError(e)
  }
}
