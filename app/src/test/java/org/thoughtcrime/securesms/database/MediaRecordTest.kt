/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.BeforeClass
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.testutil.SystemOutLogger

class MediaRecordTest {

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @Test
  fun `link preview is the first entry in the json`() {
    val json = """[{"url":"https://signal.org","title":"Signal","description":"","date":0,"attachmentId":null},{"url":"https://example.com","title":"Example"}]"""

    assertThat(record(json).linkPreview?.url).isEqualTo("https://signal.org")
  }

  @Test
  fun `link preview is null when there is no json`() {
    assertThat(record(null).linkPreview).isNull()
  }

  @Test
  fun `link preview is null when the json is empty`() {
    assertThat(record("[]").linkPreview).isNull()
  }

  @Test
  fun `link preview is null when the json is malformed`() {
    assertThat(record("not json").linkPreview).isNull()
  }

  private fun record(linkPreviewJson: String?): MediaTable.MediaRecord {
    return MediaTable.MediaRecord(
      attachment = null,
      recipientId = RecipientId.from(1),
      threadRecipientId = RecipientId.from(2),
      threadId = 1,
      messageId = 1,
      date = 0,
      isOutgoing = false,
      linkPreviewJson = linkPreviewJson
    )
  }
}
