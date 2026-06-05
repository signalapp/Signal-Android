/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.rest

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.ForwardingSink
import okio.buffer
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.BadRequestError
import org.signal.libsignal.net.RequestResult
import org.signal.network.rest.RequestSpec.Host
import org.signal.network.util.JsonUtil
import org.whispersystems.signalservice.api.messages.AttachmentTransferProgress
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.api.util.CredentialsProvider
import org.whispersystems.signalservice.api.util.Tls12SocketFactory
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory
import org.whispersystems.signalservice.internal.configuration.SignalProxy
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.configuration.SignalUrl
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager
import java.io.IOException
import java.io.OutputStream
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Optional
import java.util.Random
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.jvm.Throws
import kotlin.reflect.KClass

/**
 * A suspending REST client that handles common infrastructure like CC, cert pinning, DNS, and proxies.
 * It also standardizes responses to be [RequestResult]s.
 *
 * Only use this for requests that cannot be done over the websocket (generally CDN, storage service, etc).
 */
class SignalRestClient @JvmOverloads constructor(
  private val configuration: SignalServiceConfiguration,
  private val signalAgent: String?,
  private val credentialsProvider: CredentialsProvider? = null,
  private val automaticNetworkRetry: Boolean = true,
  private val socketTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
  private val random: Random = SecureRandom(),
  private val clientOverride: OkHttpClient? = null
) {

  companion object {
    private val TAG = Log.tag(SignalRestClient::class)

    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val RANGE_HEADER = "Range"
    private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    private const val DOWNLOAD_BUFFER_SIZE = 32 * 1024

    private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody()

    private val DEFAULT_ERROR_MAPPER = ErrorMapper { statusCode, headers, body ->
      RestStatusCodeError(statusCode, headers, body)
    }

    private fun createConnectionHolders(
      urls: Array<out SignalUrl>,
      interceptors: List<Interceptor>,
      dns: Optional<Dns>,
      proxy: Optional<SignalProxy>,
      clientOverride: OkHttpClient?
    ): Array<ConnectionHolder> {
      return urls.map { url ->
        ConnectionHolder(
          client = clientOverride ?: buildClient(url, interceptors, dns, proxy),
          url = url.url,
          hostHeader = url.hostHeader
        )
      }.toTypedArray()
    }

    private fun buildClient(
      url: SignalUrl,
      interceptors: List<Interceptor>,
      dns: Optional<Dns>,
      proxy: Optional<SignalProxy>
    ): OkHttpClient {
      try {
        val trustManagers = BlacklistingTrustManager.createFor(url.trustStore)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }

        val builder = OkHttpClient.Builder()
          .sslSocketFactory(Tls12SocketFactory(sslContext.socketFactory), trustManagers[0] as X509TrustManager)
          .connectionSpecs(url.connectionSpecs.orElse(listOf(ConnectionSpec.RESTRICTED_TLS))!!)
          .dns(dns.orElse(Dns.SYSTEM)!!)
          .connectionPool(ConnectionPool(5, 45, TimeUnit.SECONDS))

        if (proxy.isPresent) {
          builder.socketFactory(TlsProxySocketFactory(proxy.get().host, proxy.get().port, dns))
        }

        for (interceptor in interceptors) {
          builder.addInterceptor(interceptor)
        }

        return builder.build()
      } catch (e: NoSuchAlgorithmException) {
        throw AssertionError(e)
      } catch (e: KeyManagementException) {
        throw AssertionError(e)
      }
    }
  }

  private val serviceClients: Array<ConnectionHolder> = createConnectionHolders(
    configuration.signalServiceUrls,
    configuration.networkInterceptors,
    configuration.dns,
    configuration.signalProxy,
    clientOverride
  )

  private val cdnClientsMap: Map<Int, Array<ConnectionHolder>> = configuration.signalCdnUrlMap
    .mapValues { (_, urls) ->
      createConnectionHolders(
        urls = urls,
        interceptors = configuration.networkInterceptors,
        dns = configuration.dns,
        proxy = configuration.signalProxy,
        clientOverride = clientOverride
      )
    }

  private val storageClients: Array<ConnectionHolder> = createConnectionHolders(
    configuration.signalStorageUrls,
    configuration.networkInterceptors,
    configuration.dns,
    configuration.signalProxy,
    clientOverride
  )

  /**
   * Make a request, returning the raw [RestResponse] on success. Non-2xx responses are mapped to
   * a [RestStatusCodeError]. If a [progressListener] is supplied and [RequestSpec.body] is non-null,
   * upload progress will be reported as bytes are written to the wire.
   */
  suspend fun request(
    spec: RequestSpec,
    progressListener: ProgressListener? = null
  ): RequestResult<RestResponse, RestStatusCodeError> {
    return execute(spec, responseClass = null, errorMapper = DEFAULT_ERROR_MAPPER, progressListener = progressListener)
  }

  /**
   * Make a request and decode the 2xx body via [JsonUtil] (or pass through directly for [Unit],
   * [String], or [ByteArray]). Non-2xx responses are mapped to [RestStatusCodeError]. If a
   * [progressListener] is supplied and [RequestSpec.body] is non-null, upload progress will be
   * reported as bytes are written to the wire.
   */
  suspend fun <T : Any> request(
    spec: RequestSpec,
    responseClass: KClass<T>,
    progressListener: ProgressListener? = null
  ): RequestResult<T, RestStatusCodeError> {
    return execute(spec, responseClass = responseClass, errorMapper = DEFAULT_ERROR_MAPPER, progressListener = progressListener)
  }

  /**
   * Make a request returning the raw [RestResponse] on success, using a custom [ErrorMapper] for
   * non-2xx responses.
   */
  suspend fun <E : BadRequestError> request(
    spec: RequestSpec,
    errorMapper: ErrorMapper<E>,
    progressListener: ProgressListener? = null
  ): RequestResult<RestResponse, E> {
    return execute(spec, responseClass = null, errorMapper = errorMapper, progressListener = progressListener)
  }

  /**
   * Make a request, decoding the 2xx body to [T] and mapping non-2xx via the supplied
   * [ErrorMapper].
   */
  suspend fun <T : Any, E : BadRequestError> request(
    spec: RequestSpec,
    responseClass: KClass<T>,
    errorMapper: ErrorMapper<E>,
    progressListener: ProgressListener? = null
  ): RequestResult<T, E> {
    return execute(spec, responseClass = responseClass, errorMapper = errorMapper, progressListener = progressListener)
  }

  /**
   * Stream the response body for [spec] into [destination], reporting progress through
   * [progressListener] as bytes flow.
   *
   * - [offset] adds a `Range: bytes=<offset>-` header (use for resuming partial downloads). It is
   *   also used as the starting count for progress reporting.
   * - [maxSize] caps the total bytes streamed; exceeding it produces a [RequestResult.RetryableNetworkError].
   * - Cancellation propagates from coroutine cancellation and from [ProgressListener.shouldCancel].
   *
   * Non-2xx responses produce a [RequestResult.NonSuccess] using [RestStatusCodeError].
   */
  suspend fun download(
    spec: RequestSpec,
    destination: OutputStream,
    offset: Long = 0,
    maxSize: Long = Long.MAX_VALUE,
    progressListener: ProgressListener? = null
  ): RequestResult<DownloadResult, RestStatusCodeError> {
    return executeDownload(spec, destination, offset, maxSize, progressListener, DEFAULT_ERROR_MAPPER)
  }

  /**
   * Same as [download] but with a caller-supplied [ErrorMapper].
   */
  suspend fun <E : BadRequestError> download(
    spec: RequestSpec,
    destination: OutputStream,
    errorMapper: ErrorMapper<E>,
    offset: Long = 0,
    maxSize: Long = Long.MAX_VALUE,
    progressListener: ProgressListener? = null
  ): RequestResult<DownloadResult, E> {
    return executeDownload(spec, destination, offset, maxSize, progressListener, errorMapper)
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <T, E : BadRequestError> execute(
    spec: RequestSpec,
    responseClass: KClass<*>?,
    errorMapper: ErrorMapper<E>,
    progressListener: ProgressListener?
  ): RequestResult<T, E> {
    return try {
      val holder = pickHolder(spec.host)
      val effectiveBody = spec.body?.let { body ->
        if (progressListener != null) ProgressRequestBody(body, progressListener) else body
      }
      val httpRequest = buildHttpRequest(spec, holder, effectiveBody, extraHeaders = emptyMap())
      val client = newCallClient(holder)
      val response = client.newCall(httpRequest).await()

      response.use { resp ->
        val body = resp.body.bytes()
        val headers = resp.headers.toLowercaseMap()
        val code = resp.code
        if (code in 200..299) {
          val parsed = parseSuccessBody(code, headers, body, responseClass)
          RequestResult.Success(parsed as T)
        } else {
          RequestResult.NonSuccess(errorMapper.map(code, headers, body))
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  private suspend fun <E : BadRequestError> executeDownload(
    spec: RequestSpec,
    destination: OutputStream,
    offset: Long,
    maxSize: Long,
    progressListener: ProgressListener?,
    errorMapper: ErrorMapper<E>
  ): RequestResult<DownloadResult, E> {
    require(offset >= 0) { "offset must be non-negative (was $offset)" }
    require(maxSize >= 0) { "maxSize must be non-negative (was $maxSize)" }

    return try {
      val holder = pickHolder(spec.host)
      val rangeHeader = if (offset > 0) mapOf(RANGE_HEADER to "bytes=$offset-") else emptyMap()
      val httpRequest = buildHttpRequest(spec, holder, body = spec.body, extraHeaders = rangeHeader)
      val client = newCallClient(holder)
      val call = client.newCall(httpRequest)
      val response = call.await()

      response.use { resp ->
        val code = resp.code
        val headers = resp.headers.toLowercaseMap()
        if (code in 200..299) {
          val totalBytes = streamToDestination(resp, destination, offset, maxSize, progressListener, call)
          RequestResult.Success(DownloadResult(code, headers, totalBytes))
        } else {
          val errorBody = runCatching { resp.body.bytes() }.getOrNull()
          RequestResult.NonSuccess(errorMapper.map(code, headers, errorBody))
        }
      }
    } catch (e: IOException) {
      RequestResult.RetryableNetworkError(e)
    } catch (e: Throwable) {
      RequestResult.ApplicationError(e)
    }
  }

  @Throws(IOException::class)
  private fun streamToDestination(
    response: Response,
    destination: OutputStream,
    startingOffset: Long,
    maxSize: Long,
    progressListener: ProgressListener?,
    call: Call
  ): Long {
    val body = response.body
    val contentLength = body.contentLength()
    if (contentLength > 0 && contentLength + startingOffset > maxSize) {
      throw IOException("Response exceeds max size!")
    }

    val totalExpected = if (contentLength > 0) contentLength + startingOffset else -1L
    val input = body.byteStream()
    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
    var totalBytes = startingOffset

    while (true) {
      val read = input.read(buffer)
      if (read == -1) break

      if (progressListener?.shouldCancel() == true) {
        runCatching { call.cancel() }
        throw IOException("Canceled by listener check.")
      }

      destination.write(buffer, 0, read)
      totalBytes += read

      if (totalBytes > maxSize) {
        throw IOException("Response exceeded max size!")
      }

      if (progressListener != null && totalExpected > 0) {
        progressListener.onAttachmentProgress(AttachmentTransferProgress(totalExpected, totalBytes))
      }
    }

    return totalBytes
  }

  private fun pickHolder(host: Host): ConnectionHolder {
    val pool: Array<ConnectionHolder> = when (host) {
      is Host.Service -> serviceClients
      is Host.Storage -> storageClients
      is Host.Cdn -> cdnClientsMap[host.number]
        ?: throw IllegalArgumentException("No CDN configuration for number ${host.number}")
    }
    return pool[random.nextInt(pool.size)]
  }

  private fun newCallClient(holder: ConnectionHolder): OkHttpClient {
    return holder.client.newBuilder()
      .connectTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
      .readTimeout(socketTimeoutMillis, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(automaticNetworkRetry)
      .build()
  }

  private fun buildHttpRequest(
    spec: RequestSpec,
    holder: ConnectionHolder,
    body: RequestBody?,
    extraHeaders: Map<String, String>
  ): Request {
    val requestBody = body ?: when (spec.method) {
      RequestSpec.Method.POST, RequestSpec.Method.PUT, RequestSpec.Method.PATCH -> EMPTY_BODY
      else -> null
    }

    val builder = Request.Builder()
      .url(computeRequestUrl(spec.path, holder))
      .method(spec.method.value, requestBody)

    for ((key, value) in spec.headers) {
      builder.addHeader(key, value)
    }

    for ((key, value) in extraHeaders) {
      if (!spec.headers.containsKey(key)) {
        builder.addHeader(key, value)
      }
    }

    when (val auth = spec.auth) {
      is RequestSpec.Auth.None -> Unit
      is RequestSpec.Auth.Standard -> {
        val provider = checkNotNull(credentialsProvider) { "RequestSpec.Auth.Basic requires a CredentialsProvider on SignalRestClient" }
        if (!spec.headers.containsKey(AUTHORIZATION_HEADER)) {
          builder.addHeader(AUTHORIZATION_HEADER, basicAuthHeader(provider))
        } else {
          Log.w(TAG, "Requested Basic auth, but there was already an auth header. Keeping existing auth header.")
        }
      }
      is RequestSpec.Auth.Header -> {
        if (!spec.headers.containsKey(auth.name)) {
          builder.addHeader(auth.name, auth.value)
        }
      }
    }

    if (signalAgent != null) {
      builder.addHeader("X-Signal-Agent", signalAgent)
    }

    holder.hostHeader.ifPresent { builder.addHeader("Host", it) }

    return builder.build()
  }

  /**
   * Turn a [path] into a concrete [HttpUrl]. For relative paths we just append. For
   * absolute URLs (e.g. resumable upload locations the server hands us) we keep the scheme, host,
   * port, and base path from the pinned [holder] and graft the supplied path/query/fragment on top
   * — the same trick `PushServiceSocket.buildConfiguredUrl` plays so traffic stays on our
   * cert-pinned endpoints regardless of what URL the server returned.
   */
  private fun computeRequestUrl(path: String, holder: ConnectionHolder): HttpUrl {
    if (!path.startsWith("http://") && !path.startsWith("https://")) {
      return (holder.url + path).toHttpUrl()
    }

    val absolute = path.toHttpUrl()
    val base = holder.url.toHttpUrl()
    return HttpUrl.Builder()
      .scheme(base.scheme)
      .host(base.host)
      .port(base.port)
      .encodedPath(base.encodedPath)
      .addEncodedPathSegments(absolute.encodedPath.removePrefix("/"))
      .apply {
        absolute.encodedQuery?.let { encodedQuery(it) }
        absolute.encodedFragment?.let { encodedFragment(it) }
      }
      .build()
  }

  private fun parseSuccessBody(
    statusCode: Int,
    headers: Map<String, String>,
    body: ByteArray,
    responseClass: KClass<*>?
  ): Any {
    if (responseClass == null) {
      return RestResponse(statusCode, headers, body)
    }
    return when (responseClass) {
      Unit::class -> Unit
      String::class -> body.toString(Charsets.UTF_8)
      ByteArray::class -> body
      ByteString::class -> ByteString.of(*body)
      else -> JsonUtil.fromJson(body, responseClass.java)
    }
  }

  private fun basicAuthHeader(provider: CredentialsProvider): String {
    val baseIdentifier = provider.aci?.toString() ?: provider.e164
    val identifier = if (provider.deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID) {
      "$baseIdentifier.${provider.deviceId}"
    } else {
      baseIdentifier
    }

    return Credentials.basic(identifier, provider.password)
  }

  private fun okhttp3.Headers.toLowercaseMap(): Map<String, String> {
    val map = LinkedHashMap<String, String>(size)
    for (i in 0 until size) {
      map[name(i).lowercase()] = value(i)
    }
    return map
  }

  private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        if (!cont.isCancelled) cont.resumeWith(Result.failure(e))
      }

      override fun onResponse(call: Call, response: Response) {
        cont.resume(response)
      }
    })
  }

  private data class ConnectionHolder(
    val client: OkHttpClient,
    val url: String,
    val hostHeader: Optional<String>
  )

  /**
   * Wraps an outgoing [RequestBody] to report progress as bytes flow to the underlying sink. Used
   * internally when callers pass a [ProgressListener] to one of the upload-capable [request]
   * overloads.
   */
  private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val listener: ProgressListener
  ) : RequestBody() {
    override fun contentType() = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
      val total = contentLength()
      val countingSink = object : ForwardingSink(sink) {
        var written: Long = 0

        override fun write(source: Buffer, byteCount: Long) {
          super.write(source, byteCount)
          written += byteCount
          if (total > 0) {
            listener.onAttachmentProgress(AttachmentTransferProgress(total, written))
          }
        }
      }
      val buffered = countingSink.buffer()
      delegate.writeTo(buffered)
      buffered.flush()
    }
  }
}
