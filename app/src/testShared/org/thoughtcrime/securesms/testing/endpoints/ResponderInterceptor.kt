/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.endpoints

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * OkHttp interceptor that short-circuits requests to [matchHosts] (Stripe) with responses from the
 * shared [EndpointResponder], leaving all other requests untouched. Because it sits below
 * [org.signal.donations.StripeApi], the real request building and response/error parsing
 * (`checkResponseForErrors`, `getNextAction`) run against the canned bodies — so decline, failure,
 * and 3DS `next_action` payloads flow through production code exactly as they would live.
 */
class ResponderInterceptor(
  private val responder: EndpointResponder,
  private val matchHosts: Set<String> = setOf("api.stripe.com")
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    if (request.url.host !in matchHosts) {
      return chain.proceed(request)
    }

    val bodyBytes = request.body?.let { body ->
      Buffer().use { buffer ->
        body.writeTo(buffer)
        buffer.readByteArray()
      }
    } ?: ByteArray(0)

    val endpointRequest = EndpointRequest(
      method = request.method,
      path = request.url.encodedPath,
      host = request.url.host,
      bodyBytes = bodyBytes,
      headers = request.headers.names().associateWith { request.headers[it].orEmpty() },
      transport = EndpointTransport.HTTP
    )

    val response = responder.respond(endpointRequest)
    if (response.simulateIoFailure) {
      throw IOException("Simulated network failure for ${request.url}")
    }

    val builder = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(response.status)
      .message(if (response.status in 200..299) "OK" else "Error")
      .body(response.body.toResponseBody("application/json".toMediaType()))

    response.headers.forEach { (key, value) -> builder.header(key, value) }

    return builder.build()
  }
}
