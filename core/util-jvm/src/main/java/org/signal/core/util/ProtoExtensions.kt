/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("ProtoUtil")

package org.signal.core.util

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.WireField
import okio.Buffer
import okio.ByteString
import okio.utf8Size
import org.signal.core.util.logging.Log
import java.io.IOException
import java.util.LinkedList

private const val TAG = "ProtoExtension"

fun ByteString?.isEmpty(): Boolean {
  return this == null || this.size == 0
}

fun ByteString?.isNotEmpty(): Boolean {
  return this != null && this.size > 0
}

fun ByteString?.isNullOrEmpty(): Boolean {
  return this == null || this.size == 0
}

fun ByteString.nullIfEmpty(): ByteString? {
  return if (this.isEmpty()) {
    null
  } else {
    this
  }
}

/**
 * Performs the common pattern of attempting to decode a serialized proto and returning null if it fails to decode.
 */
fun <E> ProtoAdapter<E>.decodeOrNull(serialized: ByteArray): E? {
  return try {
    this.decode(serialized)
  } catch (e: IOException) {
    null
  }
}

/**
 * True if there are unknown fields anywhere inside the proto or its nested protos.
 */
fun Message<*, *>.hasUnknownFields(): Boolean {
  val allProtos = this.getInnerProtos()
  allProtos.add(this)
  for (proto in allProtos) {
    val unknownFields = proto.unknownFields
    if (unknownFields.size > 0) {
      return true
    }
  }
  return false
}

fun Message<*, *>.getUnknownEnumValue(tag: Int): Int {
  val reader = ProtoReader(Buffer().write(this.unknownFields))
  reader.forEachTag { unknownTag ->
    if (unknownTag == tag) {
      return ProtoAdapter.INT32.decode(reader)
    }
  }

  throw AssertionError("Tag $tag not found in unknown fields")
}

fun writeUnknownEnumValue(tag: Int, enumValue: Int): ByteString {
  val buffer = Buffer()
  val writer = ProtoWriter(buffer)

  @Suppress("UNCHECKED_CAST")
  (FieldEncoding.VARINT.rawProtoAdapter() as ProtoAdapter<Any>).encodeWithTag(writer, tag, enumValue.toLong())

  return buffer.readByteString()
}

/**
 * Builds a human-readable tree of serialized sizes for this proto message,
 * recursively descending into all nested message fields. Useful for debugging
 * oversized messages.
 *
 * Example output:
 * ```
 * Content(1234)
 *   dataMessage(1100)
 *     body(500)
 *     groupV2(400)
 *       groupChange(350)
 *     attachments[2](200)
 * ```
 */
fun Message<*, *>.buildSizeTree(name: String): String {
  val sb = StringBuilder()
  appendSizeTree(name, 0, sb)
  return sb.toString()
}

@Suppress("UNCHECKED_CAST")
private fun Message<*, *>.appendSizeTree(name: String, depth: Int, sb: StringBuilder) {
  val totalSize = (adapter as ProtoAdapter<Message<*, *>>).encodedSize(this)
  repeat(depth) { sb.append("  ") }
  sb.append(name).append("(").append(totalSize).append(")")

  for (field in this.javaClass.declaredFields) {
    field.getAnnotation(WireField::class.java) ?: continue

    val value = try {
      field.get(this)
    } catch (_: IllegalAccessException) {
      continue
    } ?: continue

    val fieldName = field.name

    when (value) {
      is Message<*, *> -> {
        sb.append("\n")
        value.appendSizeTree(fieldName, depth + 1, sb)
      }
      is List<*> -> {
        if (value.isNotEmpty()) {
          var listTotalSize = 0
          val isMessageList = value[0] is Message<*, *>
          for (item in value) {
            when (item) {
              is Message<*, *> -> listTotalSize += (item.adapter as ProtoAdapter<Message<*, *>>).encodedSize(item)
              is ByteString -> listTotalSize += item.size
              is String -> listTotalSize += item.utf8Size().toInt()
            }
          }
          sb.append("\n")
          repeat(depth + 1) { sb.append("  ") }
          sb.append(fieldName).append("[").append(value.size).append("](").append(listTotalSize).append(")")

          if (isMessageList) {
            for (i in value.indices) {
              sb.append("\n")
              (value[i] as Message<*, *>).appendSizeTree("${fieldName}[$i]", depth + 2, sb)
            }
          }
        }
      }
      is ByteString -> {
        if (value.size > 0) {
          sb.append("\n")
          repeat(depth + 1) { sb.append("  ") }
          sb.append(fieldName).append("(").append(value.size).append(")")
        }
      }
      is String -> {
        if (value.isNotEmpty()) {
          sb.append("\n")
          repeat(depth + 1) { sb.append("  ") }
          sb.append(fieldName).append("(").append(value.utf8Size().toInt()).append(")")
        }
      }
    }
  }

  if (unknownFields.size > 0) {
    sb.append("\n")
    repeat(depth + 1) { sb.append("  ") }
    sb.append("unknownFields(").append(unknownFields.size).append(")")
  }
}

/**
 * Recursively retrieves all inner complex proto types inside a given proto.
 */
private fun Message<*, *>.getInnerProtos(): MutableList<Message<*, *>> {
  val innerProtos: MutableList<Message<*, *>> = LinkedList()
  try {
    val fields = this.javaClass.declaredFields
    for (field in fields) {
      if (Message::class.java.isAssignableFrom(field.type)) {
        field.isAccessible = true
        val inner = field[this] as? Message<*, *>
        if (inner != null) {
          innerProtos.add(inner)
          innerProtos.addAll(inner.getInnerProtos())
        }
      }
    }
  } catch (e: IllegalAccessException) {
    Log.w(TAG, "Failed to get inner protos!", e)
  }
  return innerProtos
}
