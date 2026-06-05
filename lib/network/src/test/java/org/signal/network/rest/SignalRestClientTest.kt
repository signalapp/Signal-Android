/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RequestSpec.Host
import org.signal.network.rest.RequestSpec.Method
import org.whispersystems.signalservice.api.push.TrustStore
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl
import org.whispersystems.signalservice.internal.configuration.SignalCdsiUrl
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl
import org.whispersystems.signalservice.internal.configuration.SignalStorageUrl
import org.whispersystems.signalservice.internal.configuration.SignalSvr2Url
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Optional
import java.util.Random

class SignalRestClientTest {

  private val recordedRequests = mutableListOf<Request>()

  /** Default: 200 with an empty JSON body. Override per-test before issuing a request. */
  private var responder: (Request) -> Response = { req -> response(req, 200, "{}") }

  private val recordingClient: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(
      Interceptor { chain ->
        val request = chain.request()
        recordedRequests += request
        responder(request)
      }
    )
    .build()

  @Test
  fun `cycles through service urls based on injected random`() = runBlockingTest {
    val client = client(random = ScriptedRandom(0, 2, 1, 0))

    repeat(4) {
      client.request(RequestSpec(Method.GET, Host.Service, "/v1/ping"))
    }

    assertThat(recordedRequests.map { it.url.host }).isEqualTo(
      listOf("service-a.test", "service-c.test", "service-b.test", "service-a.test")
    )
  }

  @Test
  fun `routes to the cdn pool for the requested cdn number`() = runBlockingTest {
    val client = client(random = ScriptedRandom(0, 0))

    client.request(RequestSpec(Method.GET, Host.Cdn(2), "/file"))
    client.request(RequestSpec(Method.GET, Host.Cdn(3), "/file"))

    assertThat(recordedRequests.map { it.url.host }).isEqualTo(listOf("cdn2.test", "cdn3.test"))
  }

  @Test
  fun `routes to the storage pool`() = runBlockingTest {
    val client = client(random = ScriptedRandom(0))

    client.request(RequestSpec(Method.GET, Host.Storage, "/v1/storage"))

    assertThat(recordedRequests.single().url.host).isEqualTo("storage.test")
  }

  @Test
  fun `2xx maps to Success`() = runBlockingTest {
    responder = { req -> response(req, 200, "hello", extraHeader = "X-Foo" to "Bar") }
    val client = client()

    val result = client.request(RequestSpec(Method.GET, Host.Service, "/v1/ping"))

    assertThat(result).isInstanceOf(RequestResult.Success::class)
    val success = result as RequestResult.Success
    assertThat(success.result.statusCode).isEqualTo(200)
    assertThat(String(success.result.body)).isEqualTo("hello")
    assertThat(success.result.headers["x-foo"]).isEqualTo("Bar")
  }

  @Test
  fun `non-2xx maps to NonSuccess with default RestStatusCodeError`() = runBlockingTest {
    responder = { req -> response(req, 404, "nope") }
    val client = client()

    val result = client.request(RequestSpec(Method.GET, Host.Service, "/v1/ping"))

    assertThat(result).isInstanceOf(RequestResult.NonSuccess::class)
    val error = (result as RequestResult.NonSuccess).error
    assertThat(error.statusCode).isEqualTo(404)
  }

  @Test
  fun `non-2xx uses the supplied error mapper`() = runBlockingTest {
    responder = { req -> response(req, 500, "boom") }
    val client = client()
    val mapped = TestError(599)

    val result = client.request(
      RequestSpec(Method.GET, Host.Service, "/v1/ping"),
      ErrorMapper { _, _, _ -> mapped }
    )

    assertThat(result).isInstanceOf(RequestResult.NonSuccess::class)
    assertThat((result as RequestResult.NonSuccess).error).isSameInstanceAs(mapped)
  }

  @Test
  fun `transport IOException maps to RetryableNetworkError`() = runBlockingTest {
    responder = { throw IOException("connection reset") }
    val client = client()

    val result = client.request(RequestSpec(Method.GET, Host.Service, "/v1/ping"))

    assertThat(result).isInstanceOf(RequestResult.RetryableNetworkError::class)
  }

  @Test
  fun `unknown cdn number maps to ApplicationError`() = runBlockingTest {
    val client = client()

    val result = client.request(RequestSpec(Method.GET, Host.Cdn(99), "/file"))

    assertThat(result).isInstanceOf(RequestResult.ApplicationError::class)
  }

  private fun runBlockingTest(block: suspend () -> Unit) {
    runBlocking { block() }
  }

  private fun client(random: Random = ScriptedRandom(0)): SignalRestClient {
    return SignalRestClient(
      configuration = testConfiguration(),
      signalAgent = "test-agent",
      credentialsProvider = null,
      automaticNetworkRetry = false,
      socketTimeoutMillis = 1_000,
      random = random,
      clientOverride = recordingClient
    )
  }

  private fun testConfiguration(): SignalServiceConfiguration {
    return SignalServiceConfiguration(
      signalServiceUrls = arrayOf(
        SignalServiceUrl("https://service-a.test", DUMMY_TRUST_STORE),
        SignalServiceUrl("https://service-b.test", DUMMY_TRUST_STORE),
        SignalServiceUrl("https://service-c.test", DUMMY_TRUST_STORE)
      ),
      signalCdnUrlMap = mapOf(
        2 to arrayOf(SignalCdnUrl("https://cdn2.test", DUMMY_TRUST_STORE)),
        3 to arrayOf(SignalCdnUrl("https://cdn3.test", DUMMY_TRUST_STORE))
      ),
      signalStorageUrls = arrayOf(SignalStorageUrl("https://storage.test", DUMMY_TRUST_STORE)),
      signalCdsiUrls = emptyArray<SignalCdsiUrl>(),
      signalSvr2Urls = emptyArray<SignalSvr2Url>(),
      networkInterceptors = emptyList(),
      dns = Optional.empty(),
      signalProxy = Optional.empty(),
      systemHttpProxy = Optional.empty(),
      zkGroupServerPublicParams = ByteArray(0),
      genericServerPublicParams = ByteArray(0),
      backupServerPublicParams = ByteArray(0),
      censored = false
    )
  }

  private fun response(request: Request, code: Int, body: String, extraHeader: Pair<String, String>? = null): Response {
    val builder = Response.Builder()
      .request(request)
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .message(if (code in 200..299) "OK" else "Error")
      .body(body.toResponseBody("application/octet-stream".toMediaType()))

    if (extraHeader != null) {
      builder.header(extraHeader.first, extraHeader.second)
    }

    return builder.build()
  }

  private class TestError(val code: Int) : BadRequestError

  /** A [Random] whose `nextInt(bound)` returns a scripted sequence of values. */
  private class ScriptedRandom(vararg values: Int) : Random() {
    private val queue = ArrayDeque(values.toList())

    override fun nextInt(bound: Int): Int = queue.removeFirst()
  }

  companion object {
    private val DUMMY_TRUST_STORE = object : TrustStore {
      override fun getKeyStoreInputStream() = ByteArrayInputStream(ByteArray(0))
      override fun getKeyStorePassword() = ""
    }
  }
}
