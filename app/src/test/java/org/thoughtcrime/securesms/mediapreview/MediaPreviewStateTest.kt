/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediapreview

import android.app.Application
import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.database.MediaTable

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class MediaPreviewStateTest {

  private val hdrUri: Uri = Uri.parse("content://org.thoughtcrime.securesms/part/111")
  private val sdrUri: Uri = Uri.parse("content://org.thoughtcrime.securesms/part/222")

  @Test
  fun `shouldRenderHdr is false when no uris have been recorded`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(hdrUri)),
      position = 0,
      isInSharedAnimation = false
    )

    assertThat(state.currentMediaUri).isEqualTo(hdrUri)
    assertThat(state.shouldRenderHdr).isFalse()
  }

  @Test
  fun `shouldRenderHdr is true when the current page is recorded and no transition is running`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(hdrUri)),
      position = 0,
      isInSharedAnimation = false,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.shouldRenderHdr).isTrue()
  }

  @Test
  fun `shouldRenderHdr is false while a shared element transition is running`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(hdrUri)),
      position = 0,
      isInSharedAnimation = true,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.shouldRenderHdr).isFalse()
  }

  @Test
  fun `shouldRenderHdr is false when only another page is recorded`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(sdrUri), recordFor(hdrUri)),
      position = 0,
      isInSharedAnimation = false,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.currentMediaUri).isEqualTo(sdrUri)
    assertThat(state.shouldRenderHdr).isFalse()
  }

  @Test
  fun `shouldRenderHdr is false when the position is out of bounds`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(hdrUri)),
      position = 5,
      isInSharedAnimation = false,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.currentMediaUri).isNull()
    assertThat(state.shouldRenderHdr).isFalse()
  }

  @Test
  fun `shouldRenderHdr is false when there are no media records`() {
    val state = MediaPreviewState(
      mediaRecords = emptyList(),
      position = 0,
      isInSharedAnimation = false,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.currentMediaUri).isNull()
    assertThat(state.shouldRenderHdr).isFalse()
  }

  @Test
  fun `shouldRenderHdr is false when the current record has no attachment`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(null)),
      position = 0,
      isInSharedAnimation = false,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.currentMediaUri).isNull()
    assertThat(state.shouldRenderHdr).isFalse()
  }

  @Test
  fun `shouldRenderHdr follows the position as the user pages`() {
    val state = MediaPreviewState(
      mediaRecords = listOf(recordFor(hdrUri), recordFor(sdrUri)),
      position = 0,
      isInSharedAnimation = false,
      hdrCapableUris = setOf(hdrUri)
    )

    assertThat(state.shouldRenderHdr).isTrue()
    assertThat(state.copy(position = 1).shouldRenderHdr).isFalse()
  }

  private fun recordFor(uri: Uri?): MediaTable.MediaRecord {
    val record: MediaTable.MediaRecord = mockk()

    if (uri == null) {
      every { record.attachment } returns null
    } else {
      val attachment: DatabaseAttachment = mockk()
      every { attachment.displayUri } returns uri
      every { record.attachment } returns attachment
    }

    return record
  }
}
