/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.serialization

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json

/**
 * Helper for working with JSON.
 */
object SignalJson {

  val json = Json { ignoreUnknownKeys = true }

  inline fun <reified T> encode(input: T): Either<EncodeError, String> = either {
    try {
      json.encodeToString(input)
    } catch (e: SerializationException) {
      raise(EncodeError.BadInput(e))
    }
  }

  inline fun <reified T> encode(serializer: SerializationStrategy<T>, input: T): Either<EncodeError, String> = either {
    try {
      json.encodeToString(serializer, input)
    } catch (e: SerializationException) {
      raise(EncodeError.BadInput(e))
    }
  }

  inline fun <reified T> decode(input: String): Either<DecodeError, T> = either {
    try {
      json.decodeFromString<T>(input)
    } catch (e: SerializationException) {
      raise(DecodeError.BadInput(e))
    } catch (e: IllegalStateException) {
      raise(DecodeError.BadClassAssignment(e))
    }
  }

  fun <T> decode(deserializer: DeserializationStrategy<T>, input: String): Either<DecodeError, T> = either {
    try {
      json.decodeFromString(deserializer, input)
    } catch (e: SerializationException) {
      raise(DecodeError.BadInput(e))
    } catch (e: IllegalStateException) {
      raise(DecodeError.BadClassAssignment(e))
    }
  }

  sealed class EncodeError(val cause: Exception) {
    data class BadInput(val error: SerializationException) : EncodeError(error)
  }

  sealed class DecodeError(val cause: Exception) {
    data class BadInput(val error: SerializationException) : DecodeError(error)
    data class BadClassAssignment(val error: IllegalStateException) : DecodeError(error)
  }
}
