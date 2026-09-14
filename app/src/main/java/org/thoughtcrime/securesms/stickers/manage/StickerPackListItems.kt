/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stickers.manage

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.nullIfBlank
import org.signal.glide.compose.GlideImage
import org.signal.glide.decryptableuri.DecryptableUri
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.RoundCheckbox
import org.thoughtcrime.securesms.components.transfercontrols.TransferProgressIndicator
import org.thoughtcrime.securesms.components.transfercontrols.TransferProgressState
import org.thoughtcrime.securesms.stickers.StickerPreviewDataFactory
import org.thoughtcrime.securesms.stickers.manage.StickerPack.DownloadStatus
import org.signal.core.ui.R as CoreUiR

@Composable
fun StickerPackSectionHeader(
  text: String,
  description: String? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 24.dp, vertical = 12.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface
    )

    if (description != null) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun StickerPackRow(
  pack: StickerPack,
  menuController: DropdownMenus.MenuController,
  onForwardClick: (StickerPack) -> Unit = {},
  onInstallClick: (StickerPack) -> Unit = {},
  onCopyClick: (StickerPack) -> Unit = {},
  onRemoveClick: (StickerPack) -> Unit = {},
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

    val readyIcon = ImageVector.vectorResource(R.drawable.symbol_plus_circle_24)
    val downloadedIcon = ImageVector.vectorResource(CoreUiR.drawable.symbol_check_24)
    val downloadedTint = MaterialTheme.colorScheme.onSurfaceVariant

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
          iconContentDesc = downloadedContentDesc,
          tint = downloadedTint
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
        icon = SignalIcons.Forward.imageVector,
        text = stringResource(R.string.StickerManagement_menu_send_pack),
        onClick = {
          onForwardClick(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = SignalIcons.Link.imageVector,
        text = stringResource(R.string.StickerManagement_menu_copy_pack),
        onClick = {
          onCopyClick(pack)
          menuController.hide()
        }
      )

      if (pack.downloadStatus == DownloadStatus.NotDownloaded) {
        MenuItem(
          icon = ImageVector.vectorResource(R.drawable.symbol_plus_circle_24),
          text = stringResource(R.string.StickerManagement_menu_install_pack),
          onClick = {
            onInstallClick(pack)
            menuController.hide()
          }
        )
      }

      if (pack.isInstalled) {
        MenuItem(
          icon = SignalIcons.Trash.imageVector,
          text = stringResource(R.string.StickerManagement_menu_remove_pack),
          onClick = {
            onRemoveClick(pack)
            menuController.hide()
          }
        )
      }
    }
  }
}

@Composable
fun InstalledStickerPackRow(
  pack: StickerPack,
  multiSelectEnabled: Boolean = false,
  selected: Boolean = false,
  showDragHandle: Boolean = true,
  menuController: DropdownMenus.MenuController,
  onForwardClick: (StickerPack) -> Unit = {},
  onCopyClick: (StickerPack) -> Unit = {},
  onRemoveClick: (StickerPack) -> Unit = {},
  onSelectionToggle: (StickerPack) -> Unit = {},
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
        modifier = Modifier.padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
      )
    }

    StickerPackInfo(
      coverImageUri = DecryptableUri(pack.record.cover.uri),
      title = pack.record.title,
      author = pack.record.author.nullIfBlank(),
      showOfficialBadge = pack.isBlessed,
      modifier = Modifier.weight(1f)
    )

    if (showDragHandle) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.ic_drag_handle),
        contentDescription = stringResource(R.string.StickerManagement_accessibility_drag_handle),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .padding(horizontal = 12.dp)
          .size(24.dp)
      )
    }

    DropdownMenus.Menu(
      controller = menuController,
      offsetX = 0.dp,
      offsetY = 12.dp,
      modifier = modifier.background(SignalTheme.colors.colorSurface2)
    ) {
      MenuItem(
        icon = SignalIcons.Forward.imageVector,
        text = stringResource(R.string.StickerManagement_menu_send_pack),
        onClick = {
          onForwardClick(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = SignalIcons.Link.imageVector,
        text = stringResource(R.string.StickerManagement_menu_copy_pack),
        onClick = {
          onCopyClick(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = SignalIcons.CheckCircle.imageVector,
        text = stringResource(R.string.StickerManagement_menu_select_pack),
        onClick = {
          onSelectionToggle(pack)
          menuController.hide()
        }
      )

      MenuItem(
        icon = SignalIcons.Trash.imageVector,
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
      enableApngAnimation = true,
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

@DayNightPreviews
@Composable
private fun StickerPackSectionHeaderPreview() = Previews.Preview {
  StickerPackSectionHeader(
    text = "Signal artist series"
  )
}

@DayNightPreviews
@Composable
private fun StickerPackRowPreviewBlessed() = Previews.Preview {
  StickerPackRow(
    pack = StickerPreviewDataFactory.stickerPack(
      title = "Swoon / Faces",
      author = "Swoon",
      isBlessed = true
    ),
    menuController = DropdownMenus.MenuController()
  )
}

@DayNightPreviews
@Composable
private fun StickerPackRowPreviewNotBlessed() = Previews.Preview {
  StickerPackRow(
    pack = StickerPreviewDataFactory.stickerPack(
      title = "Day by Day",
      author = "Miguel Ángel Camprubí",
      isBlessed = false,
      downloadStatus = DownloadStatus.NotDownloaded
    ),
    menuController = DropdownMenus.MenuController()
  )
}

@DayNightPreviews
@Composable
private fun StickerPackRowPreviewDownloading() = Previews.Preview {
  StickerPackRow(
    pack = StickerPreviewDataFactory.stickerPack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = false,
      downloadStatus = DownloadStatus.InProgress
    ),
    menuController = DropdownMenus.MenuController()
  )
}

@DayNightPreviews
@Composable
private fun StickerPackRowPreviewDownloaded() = Previews.Preview {
  StickerPackRow(
    pack = StickerPreviewDataFactory.stickerPack(
      title = "Bandit the Cat",
      author = "Agnes Lee",
      isBlessed = false,
      downloadStatus = DownloadStatus.Downloaded
    ),
    menuController = DropdownMenus.MenuController()
  )
}

@DayNightPreviews
@Composable
private fun InstalledStickerPackRowPreview() = Previews.Preview {
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

@DayNightPreviews
@Composable
private fun InstalledStickerPackRowSelectModePreview() = Previews.Preview {
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
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    },
    onClick = onClick,
    modifier = modifier
  )
}

@DayNightPreviews
@Composable
private fun MenuItemPreview() = Previews.Preview {
  MenuItem(
    icon = SignalIcons.Forward.imageVector,
    text = "Forward",
    onClick = { }
  )
}
