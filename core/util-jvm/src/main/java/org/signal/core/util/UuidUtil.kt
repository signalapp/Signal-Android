/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.core.util

import okio.ByteString
import java.nio.ByteBuffer
import java.util.Optional
import java.util.UUID
import java.util.regex.Pattern

object UuidUtil {
  @JvmField
  val UNKNOWN_UUID: UUID = UUID(0, 0)
  val UNKNOWN_UUID_STRING: String = UNKNOWN_UUID.toString()

  private val UUID_PATTERN: Pattern = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE)

  fun parse(uuid: String?): Optional<UUID> {
    return Optional.ofNullable(parseOrNull(uuid))
  }

  @JvmStatic
  fun parseOrNull(uuid: String?): UUID? {
    return uuid?.takeIf { isUuid(it) }?.let { parseOrThrow(it) }
  }

  fun parseOrUnknown(uuid: String?): UUID {
    return parseOrNull(uuid) ?: UNKNOWN_UUID
  }

  @JvmStatic
  fun parseOrThrow(uuid: String): UUID {
    return UUID.fromString(uuid)
  }

  @JvmStatic
  fun parseOrThrow(bytes: ByteArray): UUID {
    val byteBuffer = ByteBuffer.wrap(bytes)
    val high = byteBuffer.getLong()
    val low = byteBuffer.getLong()

    return UUID(high, low)
  }

  fun parseOrThrow(bytes: ByteString): UUID {
    return parseOrNull(bytes.toByteArray())!!
  }

  fun isUuid(uuid: String?): Boolean {
    return uuid != null && UUID_PATTERN.matcher(uuid).matches()
  }

  @JvmStatic
  fun toByteArray(uuid: UUID): ByteArray {
    val buffer = ByteBuffer.wrap(ByteArray(16))
    buffer.putLong(uuid.mostSignificantBits)
    buffer.putLong(uuid.leastSignificantBits)

    return buffer.array()
  }

  @JvmStatic
  fun toByteString(uuid: UUID): ByteString {
    return ByteString.of(*toByteArray(uuid))
  }

  @JvmStatic
  fun fromByteString(bytes: ByteString): UUID {
    return parseOrThrow(bytes.toByteArray())
  }

  @JvmStatic
  fun fromByteStringOrNull(bytes: ByteString?): UUID? {
    if (bytes == null) {
      return null
    }

    return parseOrNull(bytes.toByteArray())
  }

  @JvmStatic
  fun getStringUUID(stringId: String?, bytes: ByteString?): String? {
    val uuid = parseOrNull(bytes)
    return uuid?.toString() ?: stringId
  }

  @JvmStatic
  fun fromByteStringOrUnknown(bytes: ByteString?): UUID {
    val uuid = fromByteStringOrNull(bytes)
    return uuid ?: UNKNOWN_UUID
  }

  fun parseOrNull(byteArray: ByteArray?): UUID? {
    return if (byteArray != null && byteArray.size == 16) parseOrThrow(byteArray) else null
  }

  fun parseOrNull(byteString: ByteString?): UUID? {
    return if (byteString != null) parseOrNull(byteString.toByteArray()) else null
  }

  fun fromByteStrings(byteStringCollection: Collection<ByteString>): List<UUID> {
    val result = ArrayList<UUID>(byteStringCollection.size)

    for (byteString in byteStringCollection) {
      result.add(fromByteString(byteString))
    }

    return result
  }

  /**
   * Keep only UUIDs that are not the [.UNKNOWN_UUID].
   */
  fun filterKnown(uuids: MutableCollection<UUID?>): MutableList<UUID?> {
    val result = ArrayList<UUID?>(uuids.size)

    for (uuid in uuids) {
      if (UNKNOWN_UUID != uuid) {
        result.add(uuid)
      }
    }

    return result
  }
}
