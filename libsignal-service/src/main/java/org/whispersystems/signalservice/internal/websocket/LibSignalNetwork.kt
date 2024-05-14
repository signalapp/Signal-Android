/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

import org.signal.core.util.orNull
import org.signal.libsignal.internal.CompletableFuture
import org.signal.libsignal.net.CdsiLookupRequest
import org.signal.libsignal.net.CdsiLookupResponse
import org.signal.libsignal.net.ChatService
import org.signal.libsignal.net.Network
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.internal.configuration.SignalProxy
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.function.Consumer

/**
 * Makes Network API more ergonomic to use with Android client types
 */
class LibSignalNetwork(val network: Network, config: SignalServiceConfiguration) {
  init {
    resetSettings(config)
  }

  fun createChatService(
    credentialsProvider: CredentialsProvider? = null
  ): ChatService {
    val username = credentialsProvider?.username ?: ""
    val password = credentialsProvider?.password ?: ""
    return network.createChatService(username, password)
  }

  fun resetSettings(config: SignalServiceConfiguration) {
    resetProxy(config.signalProxy.orNull())
  }

  private fun resetProxy(proxy: SignalProxy?) {
    if (proxy == null) {
      network.clearProxy()
    } else {
      network.setProxy(proxy.host, proxy.port)
    }
  }

  // Delegates
  @Throws(IOException::class, InterruptedException::class, ExecutionException::class)
  fun cdsiLookup(
    username: String?,
    password: String?,
    request: CdsiLookupRequest?,
    tokenConsumer: Consumer<ByteArray?>
  ): CompletableFuture<CdsiLookupResponse?>? {
    return network.cdsiLookup(username, password, request, tokenConsumer)
  }
}
