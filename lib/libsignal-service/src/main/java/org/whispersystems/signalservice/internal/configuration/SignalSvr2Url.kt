package org.whispersystems.signalservice.internal.configuration

import okhttp3.ConnectionSpec
import org.whispersystems.signalservice.api.push.TrustStore

/**
 * Configuration for reach the SVR2 service.
 */
class SignalSvr2Url(
  url: String,
  trustStore: TrustStore,
  hostHeader: String? = null,
  connectionSpec: ConnectionSpec? = null
) : SignalUrl(url, hostHeader, trustStore, connectionSpec)
