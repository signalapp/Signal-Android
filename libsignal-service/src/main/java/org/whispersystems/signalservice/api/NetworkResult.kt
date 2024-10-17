/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api

import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse
import java.io.IOException
import java.util.concurrent.TimeoutException
import kotlin.reflect.KClass

typealias StatusCodeErrorAction = (NetworkResult.StatusCodeError<*>) -> Unit

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
     * A convenience method to convert a websocket request into a network result with simple conversion of the response body to the desired class.
     * Common exceptions will be caught and translated to errors.
     */
    @JvmStatic
    fun <T : Any> fromWebSocketRequest(
      signalWebSocket: SignalWebSocket,
      request: WebSocketRequestMessage,
      clazz: KClass<T>
    ): NetworkResult<T> = try {
      val result: Result<T> = signalWebSocket.request(request)
        .map { response: WebsocketResponse -> Result.success(JsonUtil.fromJson(response.body, clazz.java)) }
        .onErrorReturn { Result.failure<T>(it) }
        .blockingGet()
      Success(result.getOrThrow())
    } catch (e: NonSuccessfulResponseCodeException) {
      StatusCodeError(e)
    } catch (e: IOException) {
      NetworkError(e)
    } catch (e: TimeoutException) {
      NetworkError(PushNetworkException(e))
    } catch (e: Throwable) {
      ApplicationError(e)
    }

    /**
     * A convenience method to convert a websocket request into a network result with the ability to convert the response to your target class.
     * Common exceptions will be caught and translated to errors.
     */
    @JvmStatic
    fun <T : Any> fromWebSocketRequest(
      signalWebSocket: SignalWebSocket,
      request: WebSocketRequestMessage,
      webSocketResponseConverter: WebSocketResponseConverter<T>
    ): NetworkResult<T> = try {
      val result = signalWebSocket.request(request)
        .map { response: WebsocketResponse -> webSocketResponseConverter.convert(response) }
        .blockingGet()
      Success(result)
    } catch (e: NonSuccessfulResponseCodeException) {
      StatusCodeError(e)
    } catch (e: IOException) {
      NetworkError(e)
    } catch (e: Throwable) {
      ApplicationError(e)
    }
  }

  /** Indicates the request was successful */
  data class Success<T>(val result: T) : NetworkResult<T>()

  /** Indicates a generic network error occurred before we were able to process a response. */
  data class NetworkError<T>(val exception: IOException) : NetworkResult<T>()

  /** Indicates we got a response, but it was a non-2xx response. */
  data class StatusCodeError<T>(val code: Int, val body: String?, val exception: NonSuccessfulResponseCodeException) : NetworkResult<T>() {
    constructor(e: NonSuccessfulResponseCodeException) : this(e.code, e.body, e)
  }

  /** Indicates that the application somehow failed in a way unrelated to network activity. Usually a runtime crash. */
  data class ApplicationError<T>(val throwable: Throwable) : NetworkResult<T>()

  /**
   * Returns the result if successful, otherwise turns the result back into an exception and throws it.
   *
   * Useful for bridging to Java, where you may want to use try-catch.
   */
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
   * ```kotlin
   * val user: NetworkResult<LocalUserModel> = NetworkResult
   *   .fromFetch { fetchRemoteUserModel() }
   *   .map { it.toLocalUserModel() }
   * ```
   */
  fun <R> map(transform: (T) -> R): NetworkResult<R> {
    return when (this) {
      is Success -> Success(transform(this.result)).runOnStatusCodeError(statusCodeErrorActions)
      is NetworkError -> NetworkError<R>(exception).runOnStatusCodeError(statusCodeErrorActions)
      is ApplicationError -> ApplicationError<R>(throwable).runOnStatusCodeError(statusCodeErrorActions)
      is StatusCodeError -> StatusCodeError<R>(code, body, exception).runOnStatusCodeError(statusCodeErrorActions)
    }
  }

  /**
   * Provides the ability to fallback to [fromFetch] if the current [NetworkResult] is non-successful.
   *
   * The [fallback] will only be triggered on non-[Success] results. You can provide a [unless] to limit what kinds of errors you fallback on
   * (the default is to fallback on every error).
   *
   * This primary usecase of this is to make a websocket request (see [fromWebSocketRequest]) and fallback to rest upon failure.
   *
   * ```kotlin
   * val user: NetworkResult<LocalUserModel> = NetworkResult
   *   .fromWebSocketRequest(websocket, request, LocalUserMode.class.java)
   *   .fallbackTo { result -> NetworkResult.fromFetch { http.getUser() } }
   * ```
   *
   * @param unless If this lamba returns true, the fallback will not be triggered.
   */
  fun fallbackToFetch(unless: (NetworkResult<T>) -> Boolean = { false }, fallback: Fetcher<T>): NetworkResult<T> {
    if (this is Success) {
      return this
    }

    return if (unless(this)) {
      fromFetch(fallback)
    } else {
      this
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
      is StatusCodeError -> StatusCodeError<R>(code, body, exception).runOnStatusCodeError(statusCodeErrorActions)
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
    fun convert(response: WebsocketResponse): T
  }
}
