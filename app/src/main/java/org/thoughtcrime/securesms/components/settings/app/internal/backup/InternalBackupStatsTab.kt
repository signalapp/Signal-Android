/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Texts
import org.signal.core.util.bytes

@Composable
fun InternalBackupStatsTab(stats: InternalBackupPlaygroundViewModel.StatsState, callbacks: StatsCallbacks) {
  val scrollState = rememberScrollState()
  Column(modifier = Modifier.verticalScroll(scrollState)) {
    Texts.SectionHeader(text = "Local Attachment State")

    if (stats.attachmentStats != null) {
      Text(text = "Attachment Count: ${stats.attachmentStats.attachmentCount}")

      Text(text = "Transit Download State:")
      stats.attachmentStats.transferStateCounts.forEach { (state, count) ->
        if (count > 0) {
          Text(text = "$state: $count")
        }
      }

      Text(text = "Valid for archive Transit Download State:")
      stats.attachmentStats.validForArchiveTransferStateCounts.forEach { (state, count) ->
        if (count > 0) {
          Text(text = "$state: $count")
        }
      }

      Spacer(modifier = Modifier.size(4.dp))

      Text(text = "Archive State:")
      stats.attachmentStats.archiveStateCounts.forEach { (state, count) ->
        if (count > 0) {
          Text(text = "$state: $count")
        }
      }

      Spacer(modifier = Modifier.size(16.dp))

      Text(text = "Unique/archived data files: ${stats.attachmentStats.attachmentFileCount}/${stats.attachmentStats.finishedAttachmentFileCount}")
      Text(text = "Unique/archived verified digest count: ${stats.attachmentStats.attachmentDigestCount}/${stats.attachmentStats.finishedAttachmentDigestCount}")
      Text(text = "Unique/expected thumbnail files: ${stats.attachmentStats.thumbnailFileCount}/${stats.attachmentStats.estimatedThumbnailCount}")
      Text(text = "Local Total: ${stats.attachmentStats.attachmentFileCount + stats.attachmentStats.thumbnailFileCount}")
      Text(text = "Expected remote total: ${stats.attachmentStats.estimatedThumbnailCount + stats.attachmentStats.finishedAttachmentDigestCount}")

      Spacer(modifier = Modifier.size(16.dp))

      Text(text = "Pending upload: ${stats.attachmentStats.pendingUploadBytes} (~${stats.attachmentStats.pendingUploadBytes.bytes.toUnitString()})")
      Text(text = "Est uploaded attachments: ${stats.attachmentStats.uploadedAttachmentBytes} (~${stats.attachmentStats.uploadedAttachmentBytes.bytes.toUnitString()})")
      Text(text = "Est uploaded thumbnails: ${stats.attachmentStats.thumbnailBytes} (~${stats.attachmentStats.thumbnailBytes.bytes.toUnitString()})")
      val total = stats.attachmentStats.thumbnailBytes + stats.attachmentStats.uploadedAttachmentBytes
      Text(text = "Est total: $total (~${total.bytes.toUnitString()})")
    } else {
      CircularProgressIndicator()
    }

    Dividers.Default()

    Texts.SectionHeader(text = "Remote State")

    if (!stats.loadingRemoteStats && stats.remoteState == null && stats.remoteFailureMsg == null) {
      Button(onClick = callbacks::loadRemoteState) {
        Text(text = "Load remote stats (expensive and long)")
      }
    } else {
      if (stats.loadingRemoteStats) {
        CircularProgressIndicator()
      } else if (stats.remoteState != null) {
        Text(text = "Media item count: ${stats.remoteState.mediaCount}")
        Text(text = "Media items sum size: ${stats.remoteState.mediaSize} (~${stats.remoteState.mediaSize.bytes.toUnitString()})")
        Text(text = "Server estimated used size: ${stats.remoteState.usedSpace} (~${stats.remoteState.usedSpace.bytes.toUnitString()})")
      } else if (stats.remoteFailureMsg != null) {
        Text(text = stats.remoteFailureMsg)
      }

      Dividers.Default()

      Texts.SectionHeader(text = "Expected vs Actual")

      if (stats.attachmentStats != null && stats.remoteState != null) {
        val finished = stats.attachmentStats.finishedAttachmentFileCount
        val thumbnails = stats.attachmentStats.thumbnailFileCount
        Text(text = "Expected Count/Actual Remote Count: ${finished + thumbnails} / ${stats.remoteState.mediaCount}")
      } else {
        CircularProgressIndicator()
      }
    }
  }
}

interface StatsCallbacks {
  fun loadRemoteState()

  companion object {
    val EMPTY = object : StatsCallbacks {
      override fun loadRemoteState() = Unit
    }
  }
}
