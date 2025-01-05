/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:JvmName("LibSignalNetworkExtensions")

package org.whispersystems.signalservice.internal.websocket

import org.signal.core.util.orNull
import org.signal.libsignal.net.ChatListener
import org.signal.libsignal.net.ChatService
import org.signal.libsignal.net.Network
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration

/**
 * Helper method to create a ChatService with optional credentials.
 */
fun Network.createChatService(
  credentialsProvider: CredentialsProvider? = null,
  receiveStories: Boolean,
  listener: ChatListener? = null
): ChatService {
  val username = credentialsProvider?.username ?: ""
  val password = credentialsProvider?.password ?: ""
  return if (username.isEmpty() && password.isEmpty()) {
    this.createUnauthChatService(listener)
  } else {
    this.createAuthChatService(username, password, receiveStories, listener)
  }
}

/**
 * Helper method to apply settings from the SignalServiceConfiguration.
 */
fun Network.applyConfiguration(config: SignalServiceConfiguration) {
  val proxy = config.signalProxy.orNull()

  if (proxy == null) {
    this.clearProxy()
  } else {
    this.setProxy(proxy.host, proxy.port)
  }

  this.setCensorshipCircumventionEnabled(config.censored)
}
