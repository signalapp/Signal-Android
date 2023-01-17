package org.signal.core.util

/**
 * Generic serialization interface for use with database and store operations.
 */
interface Serializer<T, R> {
  fun serialize(data: T): R
  fun deserialize(data: R): T
}

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