/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import okio.IOException
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase

class AttachmentPlugin : Plugin {
  companion object {
    const val PATH = "/attachment"
  }

  override val name: String = "Attachment"
  override val path: String = PATH

  override fun get(parameters: Map<String, List<String>>): PluginResult {
    var errorContent = ""

    parameters["attachment_id"]?.firstOrNull()?.let { id ->
      val attachmentId = id.toLongOrNull()?.let { AttachmentId(it) }
      if (attachmentId != null) {
        try {
          val attachment = SignalDatabase.attachments.getAttachment(attachmentId)
          if (attachment != null) {
            val inputStream = if (attachment.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE) {
              SignalDatabase.attachments.getAttachmentStream(attachmentId, 0)
            } else {
              SignalDatabase.attachments.getAttachmentThumbnailStream(attachmentId, 0)
            }
            return PluginResult.RawFileResult(attachment.size, inputStream, attachment.contentType ?: "application/octet-stream")
          } else {
            throw IOException("Missing attachment, not found for: $attachmentId")
          }
        } catch (e: IOException) {
          errorContent = "${e.javaClass}: ${e.message}"
        }
      }
    }

    val formContent = """
      <form action="$PATH" method="GET">
          <label for="number">Enter an attachment_id:</label>
          <input type="number" id="attachment_id" name="attachment_id" required>
          <button type="submit">Submit</button>
      </form>
    """.trimIndent()

    return PluginResult.RawHtmlResult("$formContent<br>$errorContent")
  }
}
