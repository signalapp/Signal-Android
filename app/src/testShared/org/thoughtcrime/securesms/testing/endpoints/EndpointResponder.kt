/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.endpoints

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Transport an [EndpointRequest] arrived on. Handlers may match on this to disambiguate the two
 * outer network edges the donation pipeline talks to: Stripe over HTTP and the Signal service over
 * the websocket.
 */
enum class EndpointTransport {
  HTTP,
  WEBSOCKET
}

/**
 * A normalized view of an outbound request, whether it originated from OkHttp (Stripe) or the
 * websocket transport (Signal service). Handlers registered on an [EndpointResponder] are matched
 * against this shape so a single "state of the world" can drive both edges.
 */
class EndpointRequest(
  val method: String,
  val path: String,
  val host: String?,
  val bodyBytes: ByteArray,
  val headers: Map<String, String>,
  val transport: EndpointTransport
) {
  val bodyString: String
    get() = String(bodyBytes)
}

/**
 * The canned response a handler returns for a matched [EndpointRequest]. [simulateIoFailure] models
 * a transport-level failure (OkHttp throws [java.io.IOException]; the websocket emits an error)
 * rather than an HTTP status.
 */
class EndpointResponse(
  val status: Int,
  val body: String = "",
  val headers: Map<String, String> = emptyMap(),
  val simulateIoFailure: Boolean = false
)

/**
 * A registry of `(request predicate) -> response` handlers shared by the HTTP interceptor
 * ([ResponderInterceptor]) and the fake websocket ([ResponderWebSocketConnection]). This is the
 * single source of truth for "what each endpoint returns" in an integration test — the successor to
 * the removed MockWebServer `ResponseMocking` helpers, feeding an interceptor and a websocket fake
 * instead of a real server.
 *
 * Handlers are consulted in reverse registration order; the last-registered match wins, so a base
 * rule can register defaults and a test can override them by registering a more specific handler
 * afterwards. An unmatched request yields a 500 so a missing stub surfaces loudly rather than
 * silently succeeding.
 */
class EndpointResponder {

  private class Handler(val matcher: (EndpointRequest) -> Boolean, val factory: (EndpointRequest) -> EndpointResponse)

  private val handlers = CopyOnWriteArrayList<Handler>()

  fun register(matcher: (EndpointRequest) -> Boolean, factory: (EndpointRequest) -> EndpointResponse) {
    handlers += Handler(matcher, factory)
  }

  fun respond(request: EndpointRequest): EndpointResponse {
    val handler = handlers.lastOrNull { it.matcher(request) }
    return handler?.factory?.invoke(request)
      ?: EndpointResponse(status = 500, body = """{"error":"No endpoint handler for ${request.method} ${request.path}"}""")
  }

  fun clear() {
    handlers.clear()
  }
}

/** Register a handler matching [method] with a [path] that the request path starts with. */
fun EndpointResponder.on(method: String, path: String, factory: (EndpointRequest) -> EndpointResponse) {
  register({ it.method.equals(method, ignoreCase = true) && it.path.startsWith(path) }, factory)
}

fun EndpointResponder.get(path: String, factory: (EndpointRequest) -> EndpointResponse) = on("GET", path, factory)

fun EndpointResponder.post(path: String, factory: (EndpointRequest) -> EndpointResponse) = on("POST", path, factory)

fun EndpointResponder.put(path: String, factory: (EndpointRequest) -> EndpointResponse) = on("PUT", path, factory)

fun EndpointResponder.delete(path: String, factory: (EndpointRequest) -> EndpointResponse) = on("DELETE", path, factory)

/** Convenience: 2xx with an optional JSON body. */
fun ok(body: String = "{}"): EndpointResponse = EndpointResponse(status = 200, body = body)

/** Convenience: a non-2xx status with an optional JSON body. */
fun failure(status: Int, body: String = ""): EndpointResponse = EndpointResponse(status = status, body = body)

/** Convenience: a transport-level failure (dropped connection / timeout). */
fun ioFailure(): EndpointResponse = EndpointResponse(status = 0, simulateIoFailure = true)

/**
 * Process-wide shared responder. The instrumentation dependency provider installs adapters that read
 * from [responder]; a test configures it in setup and clears it in teardown (mirroring the old
 * `InstrumentationApplicationDependencyProvider.clearHandlers()`).
 */
object MockEndpoints {
  val responder = EndpointResponder()
}
