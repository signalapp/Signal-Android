package org.thoughtcrime.securesms.testing

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.thoughtcrime.securesms.util.JsonUtils
import java.util.concurrent.TimeUnit

typealias ResponseFactory = (request: RecordedRequest) -> MockResponse
typealias RequestPredicate = (request: RecordedRequest) -> Boolean

/**
 * Represent an HTTP verb for mocking web requests.
 */
sealed class Verb(val requestPredicate: RequestPredicate, val responseFactory: ResponseFactory)

class Get(path: String, predicate: RequestPredicate, responseFactory: ResponseFactory) : Verb(defaultRequestPredicate("GET", path, predicate), responseFactory) {
  constructor(path: String, responseFactory: ResponseFactory) : this(path, { true }, responseFactory)
}

class Put(path: String, responseFactory: ResponseFactory) : Verb(defaultRequestPredicate("PUT", path), responseFactory)

class Post(path: String, responseFactory: ResponseFactory) : Verb(defaultRequestPredicate("POST", path), responseFactory)

fun MockResponse.success(response: Any? = null): MockResponse {
  return setResponseCode(200).apply {
    if (response != null) {
      setBody(JsonUtils.toJson(response))
    }
  }
}

fun MockResponse.failure(code: Int, response: Any? = null): MockResponse {
  return setResponseCode(code).apply {
    if (response != null) {
      setBody(JsonUtils.toJson(response))
    }
  }
}

fun MockResponse.connectionFailure(): MockResponse {
  return setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
}

fun MockResponse.timeout(): MockResponse {
  return setHeadersDelay(1, TimeUnit.DAYS)
    .setBodyDelay(1, TimeUnit.DAYS)
}

inline fun <reified T> RecordedRequest.parsedRequestBody(): T {
  val bodyString = String(body.readByteArray())
  return JsonUtils.fromJson(bodyString, T::class.java)
}

private fun defaultRequestPredicate(verb: String, path: String, predicate: RequestPredicate = { true }): RequestPredicate = { request ->
  request.method == verb && request.path.startsWith("/$path") && predicate(request)
}
