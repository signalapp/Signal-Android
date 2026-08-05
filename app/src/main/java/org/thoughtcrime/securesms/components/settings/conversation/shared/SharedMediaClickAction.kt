/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsAction
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable

/** Where a tap on the shared media rail should go, which depends on whether the attachment is actually here yet. */
fun sharedMediaClickAction(mediaRecord: MediaTable.MediaRecord, isLtr: Boolean): ConversationSettingsAction {
  val attachment = mediaRecord.attachment

  return when {
    attachment == null -> {
      ConversationSettingsAction.ShowMediaNotSentYet
    }
    attachment.displayUri == null -> {
      if (attachment.transferState == AttachmentTable.TRANSFER_RESTORE_OFFLOADED) {
        ConversationSettingsAction.DownloadMedia(mediaRecord)
      } else {
        ConversationSettingsAction.ShowMediaNotSentYet
      }
    }
    attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE &&
      attachment.transferState != AttachmentTable.TRANSFER_RESTORE_OFFLOADED -> {
      ConversationSettingsAction.ShowMediaNotSentYet
    }
    else -> {
      ConversationSettingsAction.ShowMediaPreview(mediaRecord, isLtr)
    }
  }
}
