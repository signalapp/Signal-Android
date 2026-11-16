/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediaoverview

import androidx.core.net.toUri
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.signal.core.models.database.AttachmentId

/**
 * Sample data for previews and screen tests.
 */
object MediaOverviewPreviewFixtures {

  fun item(
    id: Long,
    kind: MediaOverviewItem.Kind = MediaOverviewItem.Kind.IMAGE,
    size: Long = 1_200_000,
    isOutgoing: Boolean = false,
    fileName: String? = null,
    isOffloaded: Boolean = false,
    linkUrl: String? = null
  ): MediaOverviewItem {
    val key = if (kind == MediaOverviewItem.Kind.LINK) {
      MediaOverviewItem.Key.Message(id)
    } else {
      MediaOverviewItem.Key.Attachment(AttachmentId(id))
    }

    val uri = if (kind == MediaOverviewItem.Kind.LINK || isOffloaded) null else "content://preview/$id".toUri()

    return MediaOverviewItem(
      key = key,
      kind = kind,
      messageId = id,
      threadId = 1,
      date = 1_700_000_000_000 + id,
      formattedDate = "Nov 14, 2023",
      size = size,
      contentType = when (kind) {
        MediaOverviewItem.Kind.IMAGE -> {
          "image/jpeg"
        }
        MediaOverviewItem.Kind.VIDEO -> {
          "video/mp4"
        }
        MediaOverviewItem.Kind.AUDIO -> {
          "audio/aac"
        }
        MediaOverviewItem.Kind.DOCUMENT -> {
          "application/pdf"
        }
        MediaOverviewItem.Kind.LINK -> {
          null
        }
      },
      uri = uri,
      displayUri = uri,
      fileName = fileName,
      fileExtension = if (kind == MediaOverviewItem.Kind.DOCUMENT) "pdf" else null,
      transferState = if (isOffloaded) MediaOverviewItem.TransferState.OFFLOADED else MediaOverviewItem.TransferState.DONE,
      showPlayOverlay = kind == MediaOverviewItem.Kind.VIDEO,
      isOutgoing = isOutgoing,
      senderName = "Alice",
      threadName = "Book Club",
      isThreadGroup = true,
      linkUrl = linkUrl,
      linkTitle = if (linkUrl != null) "An interesting article" else null
    )
  }

  fun state(
    isAllThreads: Boolean = false,
    selectedTab: MediaOverviewState.Tab = MediaOverviewState.Tab.MEDIA,
    detailLayout: Boolean = false,
    dialog: MediaOverviewState.Dialog = MediaOverviewState.Dialog.None
  ): MediaOverviewState {
    return MediaOverviewState(
      isAllThreads = isAllThreads,
      title = "Book Club",
      selectedTab = selectedTab,
      sorting = if (isAllThreads) MediaOverviewState.Sorting.LARGEST else MediaOverviewState.Sorting.NEWEST,
      detailLayout = detailLayout,
      dialog = dialog
    )
  }

  fun rows(tab: MediaOverviewState.Tab, isAllThreads: Boolean = false): List<MediaOverviewRow> {
    return when (tab) {
      MediaOverviewState.Tab.MEDIA -> {
        section(MediaOverviewState.SectionHeader.Today, item(1), item(2, kind = MediaOverviewItem.Kind.VIDEO, isOutgoing = true), item(3), item(4)) +
          section(MediaOverviewState.SectionHeader.Month(year = 2023, month = 10), item(5), item(6, isOffloaded = true))
      }
      MediaOverviewState.Tab.ALL -> {
        section(
          if (isAllThreads) MediaOverviewState.SectionHeader.Large else MediaOverviewState.SectionHeader.Today,
          item(10, kind = MediaOverviewItem.Kind.DOCUMENT, fileName = "Agenda.pdf"),
          item(11, kind = MediaOverviewItem.Kind.AUDIO, isOutgoing = true),
          item(12, kind = MediaOverviewItem.Kind.VIDEO),
          item(13, kind = MediaOverviewItem.Kind.LINK, linkUrl = "https://signal.org/blog")
        )
      }
      MediaOverviewState.Tab.FILES,
      MediaOverviewState.Tab.AUDIO,
      MediaOverviewState.Tab.LINKS -> {
        emptyList()
      }
    }
  }

  fun items(tab: MediaOverviewState.Tab): List<MediaOverviewItem> {
    return rows(tab).filterIsInstance<MediaOverviewRow.Media>().map { it.item }
  }

  private val loaded = LoadStates(
    refresh = LoadState.NotLoading(endOfPaginationReached = true),
    prepend = LoadState.NotLoading(endOfPaginationReached = true),
    append = LoadState.NotLoading(endOfPaginationReached = true)
  )

  fun media(isAllThreads: Boolean = false): Map<MediaOverviewState.Tab, Flow<PagingData<MediaOverviewRow>>> {
    return MediaOverviewState.Tab.entries.associateWith { tab -> flowOf(PagingData.from(rows(tab, isAllThreads), sourceLoadStates = loaded)) }
  }

  private fun section(header: MediaOverviewState.SectionHeader, vararg items: MediaOverviewItem): List<MediaOverviewRow> {
    return listOf(MediaOverviewRow.Header(header, items.first().key)) + items.map { MediaOverviewRow.Media(it) }
  }
}
