/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import okhttp3.RequestBody

/**
 * Description of a single REST request to be executed by [SignalRestClient].
 */
data class RequestSpec(
  val method: Method,
  val host: Host,
  val path: String,
  val body: RequestBody? = null,
  val headers: Map<String, String> = emptyMap(),
  val auth: Auth = Auth.None
) {
  enum class Method(val value: String) {
    GET("GET"),
    PUT("PUT"),
    POST("POST"),
    PATCH("PATCH"),
    DELETE("DELETE"),
    HEAD("HEAD")
  }

  /** Which set of Signal URLs the request should be routed through. */
  sealed interface Host {
    /** The standard Signal service URLs. */
    data object Service : Host

    /** A specific Signal CDN. [number] selects the entry in [SignalServiceConfiguration.signalCdnUrlMap]. */
    data class Cdn(val number: Int) : Host

    /** The Signal storage service URLs. */
    data object Storage : Host
  }

  /** How (or whether) to attach authentication to the outgoing request. */
  sealed interface Auth {
    /** No auth header. */
    data object None : Auth

    /** Basic auth derived from the CredentialsProvider passed to the [SignalRestClient] constructor. */
    data object Standard : Auth

    /** A caller-supplied header. */
    data class Header(val name: String, val value: String) : Auth
  }
}
