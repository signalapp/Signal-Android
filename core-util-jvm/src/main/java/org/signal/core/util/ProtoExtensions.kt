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
import okio.Buffer
import okio.ByteString
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
