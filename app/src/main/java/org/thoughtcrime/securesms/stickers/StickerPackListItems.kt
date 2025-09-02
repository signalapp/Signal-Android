/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.RoundCheckbox
import org.thoughtcrime.securesms.components.transfercontrols.TransferProgressIndicator
import org.thoughtcrime.securesms.components.transfercontrols.TransferProgressState
import org.thoughtcrime.securesms.compose.GlideImage
import org.thoughtcrime.securesms.mms.DecryptableUri
import org.thoughtcrime.securesms.stickers.AvailableStickerPack.DownloadStatus
import org.thoughtcrime.securesms.util.DeviceProperties

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
  menuController: DropdownMenus.MenuController,
  onForwardClick: (AvailableStickerPack) -> Unit = {},
  onInstallClick: (AvailableStickerPack) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .padding(horizontal = 16.dp)
      .background(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp)
      )
      .padding(vertical = 10.dp)
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
          onStartClick = { onInstallClick(pack) }
        )

        is DownloadStatus.InProgress -> TransferProgressState.InProgress()

        is DownloadStatus.Downloaded -> TransferProgressState.Complete(
          icon = downloadedIcon,
          iconContentDesc = downloadedContentDesc
        )
      }
    }

    TransferProgressIndicator(state = transferState)

    DropdownMenus.Menu(
      controller = menuController,
      offsetX = 0.dp,
      offsetY = 12.dp,
      modifier = modifier.background(SignalTheme.colors.colorSurface2)
    ) {
      MenuItem(
        icon = ImageVector.vectorResource(R.drawable.symbol_arrow_circle_down_24),
        text = stringResource(R.string.StickerManagement_menu_install_pack),
        onClick = {
          onInstallClick(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = ImageVector.vectorResource(R.drawable.symbol_forward_24),
        text = stringResource(R.string.StickerManagement_menu_forward_pack),
        onClick = {
          onForwardClick(pack)
          menuController.hide()
        }
      )
    }
  }
}

@Composable
fun InstalledStickerPackRow(
  pack: InstalledStickerPack,
  multiSelectEnabled: Boolean = false,
  selected: Boolean = false,
  menuController: DropdownMenus.MenuController,
  onForwardClick: (InstalledStickerPack) -> Unit = {},
  onRemoveClick: (InstalledStickerPack) -> Unit = {},
  onSelectionToggle: (InstalledStickerPack) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .padding(horizontal = 16.dp)
      .background(
        color = if (selected) SignalTheme.colors.colorSurface2 else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp)
      )
      .padding(horizontal = 4.dp, vertical = 10.dp)
  ) {
    AnimatedVisibility(
      visible = multiSelectEnabled,
      enter = fadeIn() + expandHorizontally(),
      exit = fadeOut() + shrinkHorizontally()
    ) {
      RoundCheckbox(
        checked = selected,
        onCheckedChange = { onSelectionToggle(pack) },
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

    DropdownMenus.Menu(
      controller = menuController,
      offsetX = 0.dp,
      offsetY = 12.dp,
      modifier = modifier.background(SignalTheme.colors.colorSurface2)
    ) {
      MenuItem(
        icon = ImageVector.vectorResource(R.drawable.symbol_forward_24),
        text = stringResource(R.string.StickerManagement_menu_forward_pack),
        onClick = {
          onForwardClick(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = ImageVector.vectorResource(R.drawable.symbol_check_circle_24),
        text = stringResource(R.string.StickerManagement_menu_select_pack),
        onClick = {
          onSelectionToggle(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = ImageVector.vectorResource(R.drawable.symbol_trash_24),
        text = stringResource(R.string.StickerManagement_menu_remove_pack),
        onClick = {
          onRemoveClick(pack)
          menuController.hide()
        }
      )
    }
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
      enableApngAnimation = DeviceProperties.shouldAllowApngStickerAnimation(LocalContext.current),
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
    ),
    menuController = DropdownMenus.MenuController()
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
    ),
    menuController = DropdownMenus.MenuController()
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
    ),
    menuController = DropdownMenus.MenuController()
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
    ),
    menuController = DropdownMenus.MenuController()
  )
}

@SignalPreview
@Composable
private fun InstalledStickerPackRowPreview() = SignalTheme {
  InstalledStickerPackRow(
    multiSelectEnabled = false,
    menuController = DropdownMenus.MenuController(),
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
    multiSelectEnabled = true,
    menuController = DropdownMenus.MenuController(),
    pack = StickerPreviewDataFactory.installedPack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = true
    )
  )
}

@Composable
private fun MenuItem(
  icon: ImageVector,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  DropdownMenus.Item(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    text = {
      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(24.dp)
        )
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    },
    onClick = onClick,
    modifier = modifier
  )
}

@SignalPreview
@Composable
private fun MenuItemPreview() = Previews.Preview {
  MenuItem(
    icon = ImageVector.vectorResource(R.drawable.symbol_forward_24),
    text = "Forward",
    onClick = { }
  )
}
