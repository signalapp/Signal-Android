/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import okio.ByteString.Companion.toByteString
import org.junit.Test
import org.whispersystems.signalservice.internal.push.BodyRange
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.GroupContextV2
import org.whispersystems.signalservice.internal.push.SyncMessage

class BuildSizeTreeTest {

  @Test
  fun `empty message has zero encoded size`() {
    val msg = DataMessage()
    val tree = msg.buildSizeTree("DataMessage")
    assertThat(tree).isEqualTo("DataMessage(0)")
  }

  @Test
  fun `string field shows utf8 byte size`() {
    val msg = DataMessage(body = "hello")
    val tree = msg.buildSizeTree("DataMessage")

    assertThat(tree).startsWith("DataMessage(")
    assertThat(tree).contains("\n  body(5)")
  }

  @Test
  fun `multi-byte utf8 string shows correct byte size`() {
    val msg = DataMessage(body = "\uD83D\uDE00") // emoji, 4 bytes in UTF-8
    val tree = msg.buildSizeTree("DataMessage")

    assertThat(tree).contains("body(4)")
  }

  @Test
  fun `ByteString field shows raw byte size`() {
    val bytes = ByteArray(100).toByteString()
    val msg = DataMessage(profileKey = bytes)
    val tree = msg.buildSizeTree("DataMessage")

    assertThat(tree).contains("\n  profileKey(100)")
  }

  @Test
  fun `null and empty fields are omitted`() {
    val msg = DataMessage(body = "hi")
    val tree = msg.buildSizeTree("DataMessage")

    assertThat(tree).doesNotContain("profileKey")
    assertThat(tree).doesNotContain("groupV2")
    assertThat(tree).doesNotContain("attachments")
    assertThat(tree).doesNotContain("bodyRanges")
  }

  @Test
  fun `nested message field shows indented sub-tree`() {
    val msg = Content(
      dataMessage = DataMessage(body = "test")
    )
    val tree = msg.buildSizeTree("Content")

    assertThat(tree).startsWith("Content(")
    assertThat(tree).contains("\n  dataMessage(")
    assertThat(tree).contains("\n    body(4)")
  }

  @Test
  fun `repeated message field shows count and total size with elements`() {
    val ranges = listOf(
      BodyRange(start = 0, length = 5),
      BodyRange(start = 10, length = 3)
    )
    val msg = DataMessage(bodyRanges = ranges)
    val tree = msg.buildSizeTree("DataMessage")

    assertThat(tree).contains("bodyRanges[2](")
    assertThat(tree).contains("bodyRanges[0](")
    assertThat(tree).contains("bodyRanges[1](")
  }

  @Test
  fun `deeply nested structure shows correct indentation`() {
    val groupChange = ByteArray(50).toByteString()
    val masterKey = ByteArray(32).toByteString()
    val msg = Content(
      dataMessage = DataMessage(
        body = "hi",
        groupV2 = GroupContextV2(
          masterKey = masterKey,
          groupChange = groupChange,
          revision = 5
        )
      )
    )
    val tree = msg.buildSizeTree("Content")

    // depth 0
    assertThat(tree).startsWith("Content(")
    // depth 1
    assertThat(tree).contains("\n  dataMessage(")
    // depth 2
    assertThat(tree).contains("\n    body(2)")
    assertThat(tree).contains("\n    groupV2(")
    // depth 3
    assertThat(tree).contains("\n      masterKey(32)")
    assertThat(tree).contains("\n      groupChange(50)")
  }

  @Test
  fun `encoded sizes match wire adapter`() {
    val msg = Content(
      dataMessage = DataMessage(body = "hello world")
    )
    val tree = msg.buildSizeTree("Content")

    val contentSize = Content.ADAPTER.encodedSize(msg)
    val dataMessageSize = DataMessage.ADAPTER.encodedSize(msg.dataMessage!!)

    assertThat(tree).startsWith("Content($contentSize)")
    assertThat(tree).contains("\n  dataMessage($dataMessageSize)")
  }

  @Test
  fun `multiple top-level fields are all included`() {
    val msg = Content(
      dataMessage = DataMessage(body = "hi"),
      syncMessage = SyncMessage()
    )
    val tree = msg.buildSizeTree("Content")

    assertThat(tree).contains("dataMessage(")
    assertThat(tree).contains("syncMessage(")
  }

  @Test
  fun `custom root name is used`() {
    val msg = DataMessage(body = "x")
    val tree = msg.buildSizeTree("MyCustomName")

    assertThat(tree).startsWith("MyCustomName(")
  }
}
