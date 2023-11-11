/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("ProtoUtil")

package org.whispersystems.signalservice.api.util

import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import okio.Buffer
import okio.ByteString
import org.signal.libsignal.protocol.logging.Log
import java.util.LinkedList

object ProtoUtil {
  private val TAG = ProtoUtil::class.java.simpleName

  /**
   * True if there are unknown fields anywhere inside the proto or its nested protos.
   */
  @JvmStatic
  fun hasUnknownFields(rootProto: Message<*, *>): Boolean {
    val allProtos = getInnerProtos(rootProto)
    allProtos.add(rootProto)
    for (proto in allProtos) {
      val unknownFields = proto.unknownFields
      if (unknownFields.size > 0) {
        return true
      }
    }
    return false
  }

  @JvmStatic
  fun getUnknownEnumValue(proto: Message<*, *>, tag: Int): Int {
    val reader = ProtoReader(Buffer().write(proto.unknownFields))
    reader.forEachTag { unknownTag ->
      if (unknownTag == tag) {
        return ProtoAdapter.INT32.decode(reader)
      }
    }

    throw AssertionError("Tag $tag not found in unknown fields")
  }

  @JvmStatic
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
  private fun getInnerProtos(proto: Message<*, *>): MutableList<Message<*, *>> {
    val innerProtos: MutableList<Message<*, *>> = LinkedList()
    try {
      val fields = proto.javaClass.declaredFields
      for (field in fields) {
        if (Message::class.java.isAssignableFrom(field.type)) {
          field.isAccessible = true
          val inner = field[proto] as? Message<*, *>
          if (inner != null) {
            innerProtos.add(inner)
            innerProtos.addAll(getInnerProtos(inner))
          }
        }
      }
    } catch (e: IllegalAccessException) {
      Log.w(TAG, "Failed to get inner protos!", e)
    }
    return innerProtos
  }
}
