/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
@file:JvmName("LibSignalNetworkExtensions")

package org.whispersystems.signalservice.internal.websocket

import org.signal.core.util.orNull
import org.signal.libsignal.net.Network
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration

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
