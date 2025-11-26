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
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Texts
import org.signal.core.util.bytes

@Composable
fun InternalBackupStatsTab(stats: InternalBackupPlaygroundViewModel.StatsState, callbacks: StatsCallbacks) {
  val scrollState = rememberScrollState()
  Column(modifier = Modifier.verticalScroll(scrollState)) {
    if (stats.attachmentStats != null) {
      Texts.SectionHeader(text = "Local Attachments")

      Rows.TextRow(
        text = "Total attachment rows",
        label = "${stats.attachmentStats.totalAttachmentRows}"
      )

      Rows.TextRow(
        text = "Total unique data files",
        label = "${stats.attachmentStats.totalUniqueDataFiles}"
      )

      Rows.TextRow(
        text = "Total unique media names",
        label = "${stats.attachmentStats.totalUniqueMediaNames}"
      )

      Rows.TextRow(
        text = "Total unique media names eligible for upload ⭐",
        label = "${stats.attachmentStats.totalUniqueMediaNamesEligibleForUpload}"
      )

      Rows.TextRow(
        text = "Eligible attachments by status ⭐",
        label = stats.attachmentStats.archiveStatusMediaNameCounts.entries.joinToString("\n") { (status, count) -> "$status: $count" }
      )

      Rows.TextRow(
        text = "Total media names with thumbnails",
        label = "${stats.attachmentStats.mediaNamesWithThumbnailsCount}"
      )

      Rows.TextRow(
        text = "Eligible thumbnails by status ⭐",
        label = stats.attachmentStats.archiveStatusMediaNameThumbnailCounts.entries.joinToString("\n") { (status, count) -> "$status: $count" }
      )

      Rows.TextRow(
        text = "Pending attachment upload bytes ⭐",
        label = "${stats.attachmentStats.pendingAttachmentUploadBytes} (~${stats.attachmentStats.pendingAttachmentUploadBytes.bytes.toUnitString()})"
      )

      Rows.TextRow(
        text = "Last snapshot full-size count ⭐",
        label = "${stats.attachmentStats.lastSnapshotFullSizeCount}"
      )

      Rows.TextRow(
        text = "Last snapshot thumbnail count ⭐",
        label = "${stats.attachmentStats.lastSnapshotThumbnailCount}"
      )

      Rows.TextRow(
        text = "Uploaded attachment bytes ⭐",
        label = "${stats.attachmentStats.uploadedAttachmentBytes} (~${stats.attachmentStats.uploadedAttachmentBytes.bytes.toUnitString()})"
      )

      Rows.TextRow(
        text = "Uploaded thumbnail bytes (estimated)",
        label = "${stats.attachmentStats.uploadedThumbnailBytes} (~${stats.attachmentStats.uploadedThumbnailBytes.bytes.toUnitString()})"
      )

      Spacer(modifier = Modifier.size(16.dp))
    } else {
      CircularProgressIndicator()
    }

    Dividers.Default()

    Texts.SectionHeader(text = "Remote State")

    if (!stats.loadingRemoteStats && stats.remoteState == null && stats.remoteFailureMsg == null) {
      Button(onClick = callbacks::loadRemoteState) {
        Text(text = "Load remote stats (expensive and long)")
      }
    } else if (stats.remoteFailureMsg != null) {
      Text(text = stats.remoteFailureMsg)
    } else if (stats.loadingRemoteStats) {
      CircularProgressIndicator()
    } else if (stats.remoteState != null) {
      Rows.TextRow(
        text = "Total media items ⭐",
        label = "${stats.remoteState.mediaCount}"
      )

      Rows.TextRow(
        text = "Total media size ⭐",
        label = "${stats.remoteState.mediaSize} (~${stats.remoteState.mediaSize.bytes.toUnitString()})"
      )

      Rows.TextRow(
        text = "Server estimated used size",
        label = "${stats.remoteState.usedSpace} (~${stats.remoteState.usedSpace.bytes.toUnitString()})"
      )
    }

    Dividers.Default()

    Texts.SectionHeader(text = "Expected vs Actual")

    if (stats.attachmentStats != null && stats.remoteState != null) {
      Rows.TextRow(
        text = "Counts ⭐",
        label = "Local: ${stats.attachmentStats.totalUploadCount}\nRemote: ${stats.remoteState.mediaCount}"
      )

      Rows.TextRow(
        text = "Bytes ⭐",
        label = "Local: ${stats.attachmentStats.totalUploadBytes} (~${stats.attachmentStats.totalUploadBytes.bytes.toUnitString()}, thumbnails are estimated)\nRemote: ${stats.remoteState.mediaSize} (~${stats.remoteState.mediaSize.bytes.toUnitString()})"
      )
    } else {
      CircularProgressIndicator()
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
