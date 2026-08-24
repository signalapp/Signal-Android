package org.signal.network.config

import okhttp3.ConnectionSpec

/**
 * Configuration for reach the SVR2 service.
 */
class SignalSvr2Url(
  url: String,
  trustStore: TrustStore,
  hostHeader: String? = null,
  connectionSpec: ConnectionSpec? = null
) : SignalUrl(url, hostHeader, trustStore, connectionSpec)
