/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import org.signal.uicomponents.recentmediarail.RecentMedia
import org.signal.uicomponents.recentmediarail.RecentMedia.Availability
import org.signal.uicomponents.recentmediarail.RecentMediaRailPresenter
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsRepository
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MediaTable

private const val SHARED_MEDIA_LIMIT = 100

/**
 * Feeds the shared media rail for a thread. Held by each conversation settings view model, which hands it to the rail's
 * presenter.
 *
 * The rail itself only ever refers to media by position, so this also hangs onto the records it last loaded so that the
 * view model can turn a tap back into the media it stands for.
 */
class SharedMediaLoader(private val repository: ConversationSettingsRepository) : RecentMediaRailPresenter.Loader {

  @Volatile
  private var records: List<MediaTable.MediaRecord> = emptyList()

  override suspend fun load(sourceId: Long): List<RecentMedia> {
    val loaded = repository.getSharedMedia(sourceId, SHARED_MEDIA_LIMIT)
    records = loaded
    return loaded.map { it.toRecentMedia() }
  }

  fun recordAt(index: Int): MediaTable.MediaRecord? = records.getOrNull(index)
}

private fun MediaTable.MediaRecord.toRecentMedia(): RecentMedia {
  return RecentMedia(
    thumbnailUri = attachment?.displayUri,
    availability = availability(),
    thumbnailTimeUs = attachment?.transformProperties?.videoTrimStartTimeUs ?: 0
  )
}

/** Whether the attachment behind this record is actually here yet, and if not, whether we can go get it. */
private fun MediaTable.MediaRecord.availability(): Availability {
  val attachment = this.attachment

  return when {
    attachment == null -> {
      Availability.UNAVAILABLE
    }
    attachment.displayUri == null -> {
      if (attachment.transferState == AttachmentTable.TRANSFER_RESTORE_OFFLOADED) {
        Availability.RESTORABLE
      } else {
        Availability.UNAVAILABLE
      }
    }
    attachment.transferState != AttachmentTable.TRANSFER_PROGRESS_DONE &&
      attachment.transferState != AttachmentTable.TRANSFER_RESTORE_OFFLOADED -> {
      Availability.UNAVAILABLE
    }
    else -> {
      Availability.AVAILABLE
    }
  }
}
