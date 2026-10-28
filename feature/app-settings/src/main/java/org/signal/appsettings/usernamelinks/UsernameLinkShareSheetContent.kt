/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamelinks

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper

sealed interface UsernameLinkShareSheetEvent {
  /** The user tapped "Copy link". */
  data object CopyClicked : UsernameLinkShareSheetEvent

  /** The user tapped "Share". */
  data object ShareClicked : UsernameLinkShareSheetEvent
}

@VisibleForTesting
object UsernameLinkShareSheetTestTags {
  const val LINK = "username-link"
  const val BUTTON_COPY = "button-copy"
  const val BUTTON_SHARE = "button-share"
}

/**
 * The user's username link, with buttons to copy or share it.
 */
@Composable
fun UsernameLinkShareSheetContent(
  usernameLink: String,
  onEvent: (UsernameLinkShareSheetEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.verticalScroll(rememberScrollState())
  ) {
    Text(
      text = stringResource(R.string.UsernameLinkShareBottomSheet_title),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 41.dp, vertical = 24.dp)
    )

    Text(
      text = usernameLink,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(horizontal = 24.dp)
        .border(
          width = 1.dp,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          shape = RoundedCornerShape(12.dp)
        )
        .padding(all = 16.dp)
        .testTag(UsernameLinkShareSheetTestTags.LINK)
    )

    ButtonRow(
      icon = SignalIcons.Copy.imageVector,
      text = stringResource(R.string.UsernameLinkShareBottomSheet_copy_link),
      onClick = { onEvent(UsernameLinkShareSheetEvent.CopyClicked) },
      modifier = Modifier
        .padding(top = 12.dp)
        .testTag(UsernameLinkShareSheetTestTags.BUTTON_COPY)
    )

    ButtonRow(
      icon = SignalIcons.Share.imageVector,
      text = stringResource(R.string.UsernameLinkShareBottomSheet_share),
      onClick = { onEvent(UsernameLinkShareSheetEvent.ShareClicked) },
      modifier = Modifier
        .padding(bottom = 12.dp)
        .testTag(UsernameLinkShareSheetTestTags.BUTTON_SHARE)
    )
  }
}

@Composable
private fun ButtonRow(
  icon: ImageVector,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )

    Text(
      text = text,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .weight(1f)
        .padding(end = 24.dp)
    )
  }
}

@DayNightPreviews
@PreviewWrapper(SignalPreviewWrapper::class)
@Composable
private fun UsernameLinkShareSheetContentPreview() {
  UsernameLinkShareSheetContent(
    usernameLink = "https://signal.me#eufzLWmFFUYAOqnVJ4Zlt0KqXf87r59FC1hZ3r7WipjKvgzMBg7DBlY5DB5hQTjsw0",
    onEvent = {}
  )
}
