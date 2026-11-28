/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

class KeyValueDataSetTest {

  @Test
  fun `containsKey - generic`() {
    val subject = KeyValueDataSet()
    subject.putBlob("a", ByteArray(0))
    subject.putBoolean("b", true)
    subject.putFloat("c", 1.1f)
    subject.putInteger("d", 3)
    subject.putLong("e", 7L)
    subject.putString("f", "spiderman")

    assertThat(subject.containsKey("a")).isTrue()
    assertThat(subject.containsKey("b")).isTrue()
    assertThat(subject.containsKey("c")).isTrue()
    assertThat(subject.containsKey("d")).isTrue()
    assertThat(subject.containsKey("e")).isTrue()
    assertThat(subject.containsKey("f")).isTrue()
    assertThat(subject.containsKey("venom")).isFalse()
  }

  @Test
  fun `getBlob - positive, non-null`() {
    val subject = KeyValueDataSet()
    subject.putBlob("key", byteArrayOf(1))

    assertThat(subject.getBlob("key", null)).isNotNull().isEqualTo(byteArrayOf(1))
    assertThat(subject.getType("key")).isEqualTo(ByteArray::class.java)
  }

  @Test
  fun `getBlob - positive, null`() {
    val subject = KeyValueDataSet()
    subject.putBlob("key", null)

    assertThat(subject.getBlob("key", ByteArray(0))).isNull()
    assertThat(subject.getType("key")).isEqualTo(ByteArray::class.java)
  }

  @Test
  fun `getBlob - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getBlob("key", byteArrayOf(1))).isNotNull().isEqualTo(byteArrayOf(1))
  }

  @Test
  fun `getBlob - negative`() {
    val subject = KeyValueDataSet()
    subject.putBlob("key", ByteArray(0))

    assertFailure { subject.getInteger("key", 0) }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getBoolean - positive`() {
    val subject = KeyValueDataSet()
    subject.putBoolean("key", true)

    assertThat(subject.getBoolean("key", false)).isTrue()
    assertThat(subject.getType("key")).isEqualTo(Boolean::class.javaObjectType)
  }

  @Test
  fun `getBoolean - negative`() {
    val subject = KeyValueDataSet()
    subject.putBoolean("key", true)

    assertFailure { subject.getInteger("key", 0) }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getBoolean - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getBoolean("key", true)).isTrue()
  }

  @Test
  fun `getFloat - positive`() {
    val subject = KeyValueDataSet()
    subject.putFloat("key", 1.1f)

    assertThat(subject.getFloat("key", 0f)).isEqualTo(1.1f)
    assertThat(subject.getType("key")).isEqualTo(Float::class.javaObjectType)
  }

  @Test
  fun `getFloat - negative`() {
    val subject = KeyValueDataSet()
    subject.putFloat("key", 1.1f)

    assertFailure { subject.getInteger("key", 0) }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getFloat - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getFloat("key", 1.1f)).isEqualTo(1.1f)
  }

  @Test
  fun `getInteger - positive`() {
    val subject = KeyValueDataSet()
    subject.putInteger("key", 1)

    assertThat(subject.getInteger("key", 0)).isEqualTo(1)
    assertThat(subject.getType("key")).isEqualTo(Int::class.javaObjectType)
  }

  @Test
  fun `getInteger - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getInteger("key", 1)).isEqualTo(1)
  }

  @Test
  fun `getLong - positive`() {
    val subject = KeyValueDataSet()
    subject.putLong("key", 1)

    assertThat(subject.getLong("key", 0)).isEqualTo(1L)
    assertThat(subject.getType("key")).isEqualTo(Long::class.javaObjectType)
  }

  @Test
  fun `getLong - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getLong("key", 1)).isEqualTo(1L)
  }

  @Test
  fun `getInteger - stored as long`() {
    val subject = KeyValueDataSet()
    subject.putLong("key", 1)

    assertThat(subject.getInteger("key", 1)).isEqualTo(1)
  }

  @Test
  fun `getInteger - stored as long and too large for int`() {
    val subject = KeyValueDataSet()
    subject.putLong("key", Long.MAX_VALUE)

    assertFailure { subject.getInteger("key", 1) }.hasClass(ArithmeticException::class)
  }

  @Test
  fun `getLong - stored as int`() {
    val subject = KeyValueDataSet()
    subject.putInteger("key", 1)

    assertThat(subject.getLong("key", 1)).isEqualTo(1L)
  }

  @Test
  fun `getString - positive, non-null`() {
    val subject = KeyValueDataSet()
    subject.putString("key", "spiderman")

    assertThat(subject.getString("key", null)).isEqualTo("spiderman")
    assertThat(subject.getType("key")).isEqualTo(String::class.java)
  }

  @Test
  fun `getString - positive, null`() {
    val subject = KeyValueDataSet()
    subject.putString("key", null)

    assertThat(subject.getString("key", "something")).isNull()
    assertThat(subject.getType("key")).isEqualTo(String::class.java)
  }

  @Test
  fun `getString - negative`() {
    val subject = KeyValueDataSet()
    subject.putString("key", "spiderman")

    assertFailure { subject.getInteger("key", 0) }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getString - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getString("key", "spiderman")).isEqualTo("spiderman")
  }

  @Test
  fun `getNonNullBlob - positive`() {
    val subject = KeyValueDataSet()
    subject.putBlob("key", byteArrayOf(1))

    assertThat(subject.getNonNullBlob("key")).isEqualTo(byteArrayOf(1))
  }

  @Test
  fun `getNonNullBlob - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getNonNullBlob("key", byteArrayOf(2))).isEqualTo(byteArrayOf(2))
  }

  @Test
  fun `getNonNullBlob - missing with no default`() {
    val subject = KeyValueDataSet()

    assertFailure { subject.getNonNullBlob("key") }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getNonNullBlob - stored null`() {
    val subject = KeyValueDataSet()
    subject.putBlob("key", null)

    assertFailure { subject.getNonNullBlob("key", byteArrayOf(2)) }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getNonNullString - positive`() {
    val subject = KeyValueDataSet()
    subject.putString("key", "spiderman")

    assertThat(subject.getNonNullString("key", "venom")).isEqualTo("spiderman")
  }

  @Test
  fun `getNonNullString - default`() {
    val subject = KeyValueDataSet()

    assertThat(subject.getNonNullString("key", "venom")).isEqualTo("venom")
  }

  @Test
  fun `getNonNullString - missing with no default`() {
    val subject = KeyValueDataSet()

    assertFailure { subject.getNonNullString("key") }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `getNonNullString - stored null`() {
    val subject = KeyValueDataSet()
    subject.putString("key", null)

    assertFailure { subject.getNonNullString("key", "venom") }.hasClass(IllegalArgumentException::class)
  }

  @Test
  fun `remove - generic`() {
    val subject = KeyValueDataSet()
    subject.putInteger("key", 1)

    assertThat(subject.containsKey("key")).isTrue()

    subject.removeAll(listOf("key"))

    assertThat(subject.containsKey("key")).isFalse()
  }
}
