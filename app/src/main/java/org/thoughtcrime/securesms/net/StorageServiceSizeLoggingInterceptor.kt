/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.net

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.database.model.LocalMetricsEvent
import org.thoughtcrime.securesms.database.model.LocalMetricsSplit
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * We're investigating a bug around the size of storage service request and response sizes.
 * This interceptor logs the size of requests and responses to the local metrics database.
 */
class StorageServiceSizeLoggingInterceptor : Interceptor {

  companion object {
    private val TAG = Log.tag(StorageServiceSizeLoggingInterceptor::class)

    private const val KEY_REQUEST_SIZE = "storage-request-size"
    private const val KEY_RESPONSE_SIZE = "storage-response-size"
    private val PATH_REGEX = ".*/v1/storage.*".toRegex()
  }

  private val executor: Executor = SignalExecutors.UNBOUNDED

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val path = request.url.encodedPath

    if (!PATH_REGEX.matches(path)) {
      return chain.proceed(request)
    }

    val requestSize = request.body?.contentLength() ?: -1L

    val response = chain.proceed(request)
    val responseBody = response.body ?: return response

    val source = responseBody.source()
    source.request(Long.MAX_VALUE) // Buffer the entire body.
    val responseSize = source.buffer.size

    Log.d(TAG, "[${request.method} $path] Request(size = ${requestSize.bytes.toUnitString()}), Response(code = ${response.code}, size = ${responseSize.bytes.toUnitString()})")
    executor.execute {
      if (!response.isSuccessful) {
        return@execute
      }

      if (requestSize > 0) {
        val event = LocalMetricsEvent(
          createdAt = System.currentTimeMillis(),
          eventId = "$KEY_REQUEST_SIZE-${UUID.randomUUID()}}",
          eventName = "[${KEY_REQUEST_SIZE}] ${request.buildEventName()}",
          splits = mutableListOf(
            LocalMetricsSplit(
              name = "size",
              duration = requestSize,
              timeunit = TimeUnit.NANOSECONDS
            )
          ),
          timeUnit = TimeUnit.NANOSECONDS,
          extraLabel = null
        )
        LocalMetricsDatabase.getInstance(AppDependencies.application).insert(System.currentTimeMillis(), event)
      }

      if (responseSize > 0) {
        val event = LocalMetricsEvent(
          createdAt = System.currentTimeMillis(),
          eventId = "$KEY_RESPONSE_SIZE-${UUID.randomUUID()}}",
          eventName = "[${KEY_RESPONSE_SIZE}] ${request.buildEventName()}",
          splits = mutableListOf(
            LocalMetricsSplit(
              name = "size",
              duration = responseSize,
              timeunit = TimeUnit.NANOSECONDS
            )
          ),
          timeUnit = TimeUnit.NANOSECONDS,
          extraLabel = null
        )
        LocalMetricsDatabase.getInstance(AppDependencies.application).insert(System.currentTimeMillis(), event)
      }
    }

    return response
  }

  private fun Request.buildEventName(): String {
    var path = this.url.encodedPath
    val method = this.method
    if (path.matches("/v1/storage/manifest/version/\\d+".toRegex())) {
      path = "/v1/storage/manifest/version/_version"
    }

    return "$method $path"
  }
}
