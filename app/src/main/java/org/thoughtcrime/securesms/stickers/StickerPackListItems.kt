/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.transfercontrols.TransferProgressIndicator
import org.thoughtcrime.securesms.components.transfercontrols.TransferProgressState
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus

@Composable
fun StickerPackSectionHeader(
  text: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 24.dp, vertical = 12.dp)
  )
}

@Composable
fun AvailableStickerPackRow(
  pack: AvailableStickerPack,
  onInstallClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(MaterialTheme.colorScheme.surface)
      .padding(start = 24.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
  ) {
    StickerPackInfo(
      coverImageUri = DecryptableUri(pack.record.cover.uri),
      title = pack.record.title,
      author = pack.record.author.nullIfBlank(),
      showOfficialBadge = pack.isBlessed,
      modifier = Modifier.weight(1f)
    )

    val readyIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_circle_down_24)
    val downloadedIcon = ImageVector.vectorResource(R.drawable.symbol_check_24)

    val startButtonContentDesc = stringResource(R.string.StickerManagement_accessibility_download)
    val startButtonOnClickLabel = stringResource(R.string.StickerManagement_accessibility_download_pack, pack.record.title)
    val downloadedContentDesc = stringResource(R.string.StickerManagement_accessibility_downloaded_checkmark, pack.record.title)

    val transferState = remember(pack.downloadStatus) {
      when (pack.downloadStatus) {
        is DownloadStatus.NotDownloaded -> TransferProgressState.Ready(
          icon = readyIcon,
          startButtonContentDesc = startButtonContentDesc,
          startButtonOnClickLabel = startButtonOnClickLabel,
          onStartClick = onInstallClick
        )

        is DownloadStatus.InProgress -> TransferProgressState.InProgress()

        is DownloadStatus.Downloaded -> TransferProgressState.Complete(
          icon = downloadedIcon,
          iconContentDesc = downloadedContentDesc
        )
      }
    }

    TransferProgressIndicator(state = transferState)
  }
}

@Composable
fun InstalledStickerPackRow(
  pack: InstalledStickerPack,
  multiSelectModeEnabled: Boolean = false,
  checked: Boolean = false,
  onCheckedChange: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .background(MaterialTheme.colorScheme.surface)
      .padding(12.dp)
  ) {
    if (multiSelectModeEnabled) {
      Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(end = 8.dp)
      )
    }

    StickerPackInfo(
      coverImageUri = DecryptableUri(pack.record.cover.uri),
      title = pack.record.title,
      author = pack.record.author.nullIfBlank(),
      showOfficialBadge = pack.isBlessed,
      modifier = Modifier.weight(1f)
    )

    Icon(
      imageVector = ImageVector.vectorResource(id = R.drawable.ic_drag_handle),
      contentDescription = stringResource(R.string.StickerManagement_accessibility_drag_handle),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier
        .padding(horizontal = 12.dp)
        .size(24.dp)
    )
  }
}

@Composable
private fun StickerPackInfo(
  coverImageUri: DecryptableUri,
  title: String,
  author: String?,
  showOfficialBadge: Boolean,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier.fillMaxWidth()
  ) {
    GlideImage(
      model = coverImageUri,
      modifier = Modifier
        .padding(end = 16.dp)
        .size(56.dp)
    )

    Column(
      modifier = Modifier.align(Alignment.CenterVertically)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface
        )

        if (showOfficialBadge) {
          Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_official_20),
            contentDescription = null,
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .size(16.dp)
          )
        }
      }
      Text(
        text = author ?: stringResource(R.string.StickerManagement_author_unknown),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@SignalPreview
@Composable
private fun StickerPackSectionHeaderPreview() = SignalTheme {
  StickerPackSectionHeader(
    text = "Signal artist series"
  )
}

@SignalPreview
@Composable
private fun AvailableStickerPackRowPreviewBlessed() = SignalTheme {
  AvailableStickerPackRow(
    pack = StickerPreviewDataFactory.availablePack(
      title = "Swoon / Faces",
      author = "Swoon",
      isBlessed = true
    )
  )
}

@SignalPreview
@Composable
private fun AvailableStickerPackRowPreviewNotBlessed() = SignalTheme {
  AvailableStickerPackRow(
    pack = StickerPreviewDataFactory.availablePack(
      title = "Day by Day",
      author = "Miguel Ángel Camprubí",
      isBlessed = false,
      downloadStatus = DownloadStatus.NotDownloaded
    )
  )
}

@SignalPreview
@Composable
private fun AvailableStickerPackRowPreviewDownloading() = SignalTheme {
  AvailableStickerPackRow(
    pack = StickerPreviewDataFactory.availablePack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = false,
      downloadStatus = DownloadStatus.InProgress
    )
  )
}

@SignalPreview
@Composable
private fun AvailableStickerPackRowPreviewDownloaded() = SignalTheme {
  AvailableStickerPackRow(
    pack = StickerPreviewDataFactory.availablePack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = false,
      downloadStatus = DownloadStatus.Downloaded
    )
  )
}

@SignalPreview
@Composable
private fun InstalledStickerPackRowPreview() = SignalTheme {
  InstalledStickerPackRow(
    multiSelectModeEnabled = false,
    pack = StickerPreviewDataFactory.installedPack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = true
    )
  )
}

@SignalPreview
@Composable
private fun InstalledStickerPackRowSelectModePreview() = SignalTheme {
  InstalledStickerPackRow(
    multiSelectModeEnabled = true,
    pack = StickerPreviewDataFactory.installedPack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = true
    )
  )
}
