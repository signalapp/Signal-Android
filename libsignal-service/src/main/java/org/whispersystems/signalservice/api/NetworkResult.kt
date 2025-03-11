/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.signal.core.util.concurrent.safeBlockingGet
import org.whispersystems.signalservice.api.NetworkResult.StatusCodeError
import org.whispersystems.signalservice.api.NetworkResult.Success
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.time.Duration

typealias StatusCodeErrorAction = (StatusCodeError<*>) -> Unit

/**
 * A helper class that wraps the result of a network request, turning common exceptions
 * into sealed classes, with optional request chaining.
 *
 * This was designed to be a middle ground between the heavy reliance on specific exceptions
 * in old network code (which doesn't translate well to kotlin not having checked exceptions)
 * and plain rx, which still doesn't free you from having to catch exceptions and translate
 * things to sealed classes yourself.
 *
 * If you have a very complicated network request with lots of different possible response types
 * based on specific errors, this isn't for you. You're likely better off writing your own
 * sealed class. However, for the majority of requests which just require getting a model from
 * the success case and the status code of the error, this can be quite convenient.
 */
sealed class NetworkResult<T>(
  private val statusCodeErrorActions: MutableSet<StatusCodeErrorAction> = mutableSetOf()
) {
  companion object {
    /**
     * A convenience method to capture the common case of making a request.
     * Perform the network action in the [fetcher], returning your result.
     * Common exceptions will be caught and translated to errors.
     */
    @JvmStatic
    fun <T> fromFetch(fetcher: Fetcher<T>): NetworkResult<T> = try {
      Success(fetcher.fetch())
    } catch (e: NonSuccessfulResponseCodeException) {
      StatusCodeError(e)
    } catch (e: IOException) {
      NetworkError(e)
    } catch (e: Throwable) {
      ApplicationError(e)
    }

    /**
     * A convenience method to convert a websocket request into a network result.
     * Common HTTP errors will be translated to [StatusCodeError]s.
     */
    @JvmStatic
    fun fromWebSocketRequest(
      signalWebSocket: SignalWebSocket,
      request: WebSocketRequestMessage
    ): NetworkResult<Unit> = fromWebSocketRequest(
      signalWebSocket = signalWebSocket,
      request = request,
      clazz = Unit::class
    )

    /**
     * A convenience method to convert a websocket request into a network result with simple conversion of the response body to the desired class.
     * Common HTTP errors will be translated to [StatusCodeError]s.
     */
    @JvmStatic
    fun <T : Any> fromWebSocketRequest(
      signalWebSocket: SignalWebSocket,
      request: WebSocketRequestMessage,
      clazz: KClass<T>,
      timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT
    ): NetworkResult<T> {
      return fromWebSocketRequest(
        signalWebSocket = signalWebSocket,
        request = request,
        timeout = timeout,
        webSocketResponseConverter = DefaultWebSocketConverter(clazz)
      )
    }

    /**
     * A convenience method to convert a websocket request into a network result with the ability to fully customize the conversion of the response.
     * Common HTTP errors will be translated to [StatusCodeError]s.
     */
    @JvmStatic
    fun <T : Any> fromWebSocketRequest(
      signalWebSocket: SignalWebSocket,
      request: WebSocketRequestMessage,
      timeout: Duration = WebSocketConnection.DEFAULT_SEND_TIMEOUT,
      webSocketResponseConverter: WebSocketResponseConverter<T>
    ): NetworkResult<T> = try {
      val result: Result<NetworkResult<T>> = signalWebSocket.request(request, timeout)
        .map { response: WebsocketResponse -> Result.success(webSocketResponseConverter.convert(response)) }
        .onErrorReturn { Result.failure(it) }
        .safeBlockingGet()

      result.getOrThrow()
    } catch (e: NonSuccessfulResponseCodeException) {
      StatusCodeError(e)
    } catch (e: IOException) {
      NetworkError(e)
    } catch (e: TimeoutException) {
      NetworkError(PushNetworkException(e))
    } catch (e: InterruptedException) {
      NetworkError(PushNetworkException(e))
    } catch (e: Throwable) {
      ApplicationError(e)
    }

    /**
     * Wraps a local operation, [block], that may throw an exception that should be wrapped in an [ApplicationError]
     * and abort downstream network requests that directly depend on the output of the local operation. Should
     * be used almost exclusively prior to a [then].
     */
    fun <T : Any> fromLocal(block: () -> T): NetworkResult<T> {
      return try {
        Success(block())
      } catch (e: Throwable) {
        ApplicationError(e)
      }
    }

    /**
     * Runs [operation] to perform a network call. If [shouldRetry] returns false for the result, then returns it. Otherwise will call [operation] repeatedly
     * until [shouldRetry] returns false or is called [maxAttempts] number of times.
     *
     * @param maxAttempts Max attempts to try the network operation, must be 1 or more, default is 5
     * @param shouldRetry Predicate to determine if network operation should be retried, default is any [NetworkError] result is retried
     * @param logAttempt Log each attempt before [operation] is called, default is noop
     * @param operation Network operation that can be called repeatedly for each attempt
     */
    fun <T : Any?> withRetry(
      maxAttempts: Int = 5,
      shouldRetry: (NetworkResult<T>) -> Boolean = { it is NetworkError },
      logAttempt: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
      operation: () -> NetworkResult<T>
    ): NetworkResult<T> {
      require(maxAttempts > 0)

      lateinit var result: NetworkResult<T>
      for (attempt in 0 until maxAttempts) {
        logAttempt(attempt, maxAttempts)
        result = operation()

        if (!shouldRetry(result)) {
          return result
        }
      }

      return result
    }
  }

  /** Indicates the request was successful */
  data class Success<T>(val result: T) : NetworkResult<T>()

  /** Indicates a generic network error occurred before we were able to process a response. */
  data class NetworkError<T>(val exception: IOException) : NetworkResult<T>()

  /** Indicates we got a response, but it was a non-2xx response. */
  data class StatusCodeError<T>(val code: Int, val stringBody: String?, val binaryBody: ByteArray?, val headers: Map<String, String>, val exception: NonSuccessfulResponseCodeException) : NetworkResult<T>() {
    constructor(e: NonSuccessfulResponseCodeException) : this(e.code, e.stringBody, e.binaryBody, e.headers, e)

    inline fun <reified T> parseJsonBody(): T? {
      return try {
        if (stringBody != null) {
          JsonUtil.fromJsonResponse(stringBody, T::class.java)
        } else if (binaryBody != null) {
          JsonUtil.fromJsonResponse(binaryBody, T::class.java)
        } else {
          null
        }
      } catch (e: MalformedRequestException) {
        null
      }
    }
  }

  /** Indicates that the application somehow failed in a way unrelated to network activity. Usually a runtime crash. */
  data class ApplicationError<T>(val throwable: Throwable) : NetworkResult<T>()

  /**
   * Returns the result if successful, otherwise turns the result back into an exception and throws it.
   *
   * Useful for bridging to Java, where you may want to use try-catch.
   */
  @Throws(NonSuccessfulResponseCodeException::class, IOException::class, Throwable::class)
  fun successOrThrow(): T {
    when (this) {
      is Success -> return result
      is NetworkError -> throw exception
      is StatusCodeError -> throw exception
      is ApplicationError -> throw throwable
    }
  }

  /**
   * Returns the [Throwable] associated with the result, or null if the result is successful.
   */
  fun getCause(): Throwable? {
    return when (this) {
      is Success -> null
      is NetworkError -> exception
      is StatusCodeError -> exception
      is ApplicationError -> throwable
    }
  }

  /**
   * Takes the output of one [NetworkResult] and transforms it into another if the operation is successful.
   * If it's non-successful, [transform] lambda is not run, and instead the original failure will be propagated.
   * Useful for changing the type of a result.
   *
   * If an exception is thrown during [transform], this is mapped to an [ApplicationError].
   *
   * ```kotlin
   * val user: NetworkResult<LocalUserModel> = NetworkResult
   *   .fromFetch { fetchRemoteUserModel() }
   *   .map { it.toLocalUserModel() }
   * ```
   */
  fun <R> map(transform: (T) -> R): NetworkResult<R> {
    return when (this) {
      is Success -> {
        try {
          Success(transform(this.result)).runOnStatusCodeError(statusCodeErrorActions)
        } catch (e: Throwable) {
          ApplicationError<R>(e).runOnStatusCodeError(statusCodeErrorActions)
        }
      }

      is NetworkError -> NetworkError<R>(exception).runOnStatusCodeError(statusCodeErrorActions)
      is ApplicationError -> ApplicationError<R>(throwable).runOnStatusCodeError(statusCodeErrorActions)
      is StatusCodeError -> StatusCodeError<R>(code, stringBody, binaryBody, headers, exception).runOnStatusCodeError(statusCodeErrorActions)
    }
  }

  /**
   * Takes the output of one [NetworkResult] and passes it as the input to another if the operation is successful.
   * If it's non-successful, the [result] lambda is not run, and instead the original failure will be propagated.
   * Useful for chaining operations together.
   *
   * ```kotlin
   * val networkResult: NetworkResult<MyData> = NetworkResult
   *   .fromFetch { fetchAuthCredential() }
   *   .then {
   *     NetworkResult.fromFetch { credential -> fetchData(credential) }
   *   }
   * ```
   */
  fun <R> then(result: (T) -> NetworkResult<R>): NetworkResult<R> {
    return when (this) {
      is Success -> result(this.result).runOnStatusCodeError(statusCodeErrorActions)
      is NetworkError -> NetworkError<R>(exception).runOnStatusCodeError(statusCodeErrorActions)
      is ApplicationError -> ApplicationError<R>(throwable).runOnStatusCodeError(statusCodeErrorActions)
      is StatusCodeError -> StatusCodeError<R>(code, stringBody, binaryBody, headers, exception).runOnStatusCodeError(statusCodeErrorActions)
    }
  }

  /**
   * Will perform an operation if the result at this point in the chain is successful. Note that it runs if the chain is _currently_ successful. It does not
   * depend on anything further down the chain.
   *
   * ```kotlin
   * val networkResult: NetworkResult<MyData> = NetworkResult
   *   .fromFetch { fetchAuthCredential() }
   *   .runIfSuccessful { storeMyCredential(it) }
   * ```
   */
  fun runIfSuccessful(result: (T) -> Unit): NetworkResult<T> {
    if (this is Success) {
      result(this.result)
    }
    return this
  }

  /**
   * Specify an action to be run when a status code error occurs. When a result is a [StatusCodeError] or is transformed into one further down the chain via
   * a future [map] or [then], this code will be run. There can only ever be a single status code error in a chain, and therefore this lambda will only ever
   * be run a single time.
   *
   * This is a low-visibility way of doing things, so use sparingly.
   *
   * ```kotlin
   * val result = NetworkResult
   *   .fromFetch { getAuth() }
   *   .runOnStatusCodeError { error -> logError(error) }
   *   .then { credential ->
   *     NetworkResult.fromFetch { fetchUserDetails(credential) }
   *   }
   * ```
   */
  fun runOnStatusCodeError(action: StatusCodeErrorAction): NetworkResult<T> {
    return runOnStatusCodeError(setOf(action))
  }

  internal fun runOnStatusCodeError(actions: Collection<StatusCodeErrorAction>): NetworkResult<T> {
    if (actions.isEmpty()) {
      return this
    }

    statusCodeErrorActions += actions

    if (this is StatusCodeError) {
      statusCodeErrorActions.forEach { it.invoke(this) }
      statusCodeErrorActions.clear()
    }

    return this
  }

  fun interface Fetcher<T> {
    @Throws(Exception::class)
    fun fetch(): T
  }

  fun interface WebSocketResponseConverter<T> {
    @Throws(Exception::class)
    fun convert(response: WebsocketResponse): NetworkResult<T>
  }

  class DefaultWebSocketConverter<T : Any>(private val responseJsonClass: KClass<T>) : WebSocketResponseConverter<T> {
    override fun convert(response: WebsocketResponse): NetworkResult<T> {
      return if (response.status < 200 || response.status > 299) {
        response.toStatusCodeError()
      } else {
        response.toSuccess(responseJsonClass)
      }
    }
  }

  class LongPollingWebSocketConverter<T : Any>(private val responseJsonClass: KClass<T>) : WebSocketResponseConverter<T> {
    override fun convert(response: WebsocketResponse): NetworkResult<T> {
      return if (response.status == 204 || response.status < 200 || response.status > 299) {
        response.toStatusCodeError()
      } else {
        response.toSuccess(responseJsonClass)
      }
    }
  }
}

private fun <T : Any> WebsocketResponse.toStatusCodeError(): NetworkResult<T> {
  return StatusCodeError(NonSuccessfulResponseCodeException(this.status, "", this.body, this.headers))
}

private fun <T : Any> WebsocketResponse.toSuccess(responseJsonClass: KClass<T>): NetworkResult<T> {
  return when (responseJsonClass) {
    Unit::class -> Success(responseJsonClass.cast(Unit))
    String::class -> Success(responseJsonClass.cast(this.body))
    else -> Success(JsonUtil.fromJson(this.body, responseJsonClass.java))
  }
}
