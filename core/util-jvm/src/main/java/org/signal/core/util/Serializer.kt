package org.signal.core.util

/**
 * Generalized serializer for finer control
 */
interface BaseSerializer<Data, Input, Output> {
  fun serialize(data: Data): Output
  fun deserialize(input: Input): Data
}

/**
 * Generic serialization interface for use with database and store operations.
 */
interface Serializer<T, R> : BaseSerializer<T, R, R>

interface StringSerializer<T> : Serializer<T, String>

interface IntSerializer<T> : Serializer<T, Int>

interface LongSerializer<T> : Serializer<T, Long>

interface ByteSerializer<T> : Serializer<T, ByteArray>

object StringStringSerializer : StringSerializer<String?> {

  override fun serialize(data: String?): String {
    return data ?: ""
  }

  override fun deserialize(data: String): String {
    return data
  }
}
