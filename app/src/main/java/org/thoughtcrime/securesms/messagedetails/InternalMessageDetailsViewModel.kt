/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.messagedetails

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.internal.util.JsonUtil

class InternalMessageDetailsViewModel(val messageId: Long) : ViewModel() {

  private val _state: MutableState<ViewState?> = mutableStateOf(null)
  val state: State<ViewState?> = _state

  init {
    viewModelScope.launch(Dispatchers.IO) {
      val messageRecord = SignalDatabase.messages.getMessageRecord(messageId)
      val attachments = SignalDatabase.attachments.getAttachmentsForMessage(messageId)

      _state.value = ViewState(
        id = messageRecord.id,
        sentTimestamp = messageRecord.dateSent,
        receivedTimestamp = messageRecord.dateReceived,
        serverSentTimestamp = messageRecord.serverTimestamp,
        from = messageRecord.fromRecipient.id,
        to = messageRecord.toRecipient.id,
        attachments = attachments.map { attachment ->
          val info = SignalDatabase.attachments.getDataFileInfo(attachment.attachmentId)

          AttachmentInfo(
            id = attachment.attachmentId.id,
            contentType = attachment.contentType,
            size = attachment.size,
            fileName = attachment.fileName,
            hashStart = info?.hashStart,
            hashEnd = info?.hashEnd,
            transformProperties = info?.transformProperties?.let { JsonUtil.toJson(it) } ?: "null"
          )
        }
      )
    }
  }

  data class ViewState(
    val id: Long,
    val sentTimestamp: Long,
    val receivedTimestamp: Long,
    val serverSentTimestamp: Long,
    val from: RecipientId,
    val to: RecipientId,
    val attachments: List<AttachmentInfo>
  )

  data class AttachmentInfo(
    val id: Long,
    val contentType: String?,
    val size: Long,
    val fileName: String?,
    val hashStart: String?,
    val hashEnd: String?,
    val transformProperties: String?
  )
}
