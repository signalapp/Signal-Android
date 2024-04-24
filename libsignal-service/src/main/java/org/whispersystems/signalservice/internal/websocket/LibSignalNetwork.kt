/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.websocket

import org.signal.libsignal.net.ChatService
import org.signal.libsignal.net.Network
import org.whispersystems.signalservice.api.util.CredentialsProvider

/**
 * Makes Network API more ergonomic to use with Android client types
 */
class LibSignalNetwork(private val inner: Network) {
  fun createChatService(
    credentialsProvider: CredentialsProvider? = null
  ): ChatService {
    val username = credentialsProvider?.username ?: ""
    val password = credentialsProvider?.password ?: ""
    return inner.createChatService(username, password)
  }
}
