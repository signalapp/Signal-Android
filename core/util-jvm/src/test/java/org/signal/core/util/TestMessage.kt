/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import com.squareup.wire.EnumAdapter
import com.squareup.wire.FieldEncoding
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.ReverseProtoWriter
import com.squareup.wire.Syntax
import com.squareup.wire.WireEnum
import com.squareup.wire.WireField
import okio.ByteString
import kotlin.jvm.JvmField

class TestMessage(
  @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING")
  @JvmField
  val name: String = "",

  @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#INT64")
  @JvmField
  val id: Long = 0L,

  @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#BYTES")
  @JvmField
  val data: ByteString = ByteString.EMPTY,

  @field:WireField(tag = 4, adapter = "org.signal.core.util.TestMessage${'$'}TestEnum#ADAPTER")
  @JvmField
  val status: TestEnum? = null,

  @field:WireField(tag = 5, adapter = "org.signal.core.util.TestMessage${'$'}Nested#ADAPTER")
  @JvmField
  val nested: Nested? = null,

  @field:WireField(tag = 6, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.REPEATED)
  @JvmField
  val tags: List<String> = emptyList(),

  unknownFields: ByteString = ByteString.EMPTY
) : Message<TestMessage, Nothing>(ADAPTER, unknownFields) {

  override fun newBuilder(): Nothing = throw UnsupportedOperationException()

  enum class TestEnum(override val value: Int) : WireEnum {
    FIRST(0),
    SECOND(1);

    companion object {
      @JvmField
      val ADAPTER: ProtoAdapter<TestEnum> = object : EnumAdapter<TestEnum>(TestEnum::class, Syntax.PROTO_3, FIRST) {
        override fun fromValue(value: Int): TestEnum? = entries.find { it.value == value }
      }
    }
  }

  class Nested(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING")
    @JvmField
    val label: String = "",

    unknownFields: ByteString = ByteString.EMPTY
  ) : Message<Nested, Nothing>(ADAPTER, unknownFields) {

    override fun newBuilder(): Nothing = throw UnsupportedOperationException()

    companion object {
      @JvmField
      val ADAPTER: ProtoAdapter<Nested> = object : ProtoAdapter<Nested>(
        FieldEncoding.LENGTH_DELIMITED,
        Nested::class,
        null,
        Syntax.PROTO_3,
        null,
        null
      ) {
        override fun encodedSize(value: Nested): Int = 0
        override fun encode(writer: ProtoWriter, value: Nested) = Unit
        override fun encode(writer: ReverseProtoWriter, value: Nested) = Unit
        override fun decode(reader: ProtoReader): Nested = Nested()
        override fun redact(value: Nested): Nested = value
      }
    }
  }

  companion object {
    @JvmField
    val ADAPTER: ProtoAdapter<TestMessage> = object : ProtoAdapter<TestMessage>(
      FieldEncoding.LENGTH_DELIMITED,
      TestMessage::class,
      null,
      Syntax.PROTO_3,
      null,
      null
    ) {
      override fun encodedSize(value: TestMessage): Int = 0
      override fun encode(writer: ProtoWriter, value: TestMessage) = Unit
      override fun encode(writer: ReverseProtoWriter, value: TestMessage) = Unit
      override fun decode(reader: ProtoReader): TestMessage = TestMessage()
      override fun redact(value: TestMessage): TestMessage = value
    }
  }
}
