/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.appSettingsScreen
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.avatar.compose.AvatarColorGrid
import org.thoughtcrime.securesms.avatar.compose.AvatarImage

@VisibleForTesting
object VectorAvatarCreationTestTags {
  const val AVATAR = "avatar"
  const val DONE_BUTTON = "done-button"
  const val SCROLL_CONTAINER = "scroll-container"
}

/**
 * Recolors one of the default vector avatars.
 */
@Composable
fun VectorAvatarCreationScreen(
  state: VectorAvatarCreationState,
  onEvent: (VectorAvatarCreationEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.TextAvatarCreationFragment__preview),
    onNavigationClick = { onEvent(VectorAvatarCreationEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    bottomBar = {
      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(end = 16.dp, bottom = 16.dp)
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(VectorAvatarCreationEvent.DoneClicked) },
          modifier = Modifier.testTag(VectorAvatarCreationTestTags.DONE_BUTTON)
        ) {
          Text(text = stringResource(R.string.TextAvatarCreationFragment__done))
        }
      }
    },
    expandableTitle = true,
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .appSettingsScreen()
        .testTag(VectorAvatarCreationTestTags.SCROLL_CONTAINER)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp)
      ) {
        AvatarImage(
          avatar = state.currentAvatar,
          contentDescription = stringResource(R.string.AvatarPickerFragment__avatar_preview),
          modifier = Modifier
            .padding(top = 24.dp)
            .size(dimensionResource(R.dimen.avatar_picker_image_width))
            .clip(CircleShape)
            .testTag(VectorAvatarCreationTestTags.AVATAR)
        )
      }

      Dividers.Default(modifier = Modifier.padding(top = 16.dp))

      Texts.SectionHeader(text = stringResource(R.string.VectorAvatarCreationFragment__select_a_color))

      AvatarColorGrid(
        selected = state.currentAvatar.color,
        onColorSelected = { onEvent(VectorAvatarCreationEvent.ColorSelected(it)) },
        modifier = Modifier
          .horizontalGutters()
          .padding(bottom = 24.dp)
      )
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@AllDevicePreviews
@Composable
private fun VectorAvatarCreationScreenPreview() {
  VectorAvatarCreationScreen(
    state = VectorAvatarCreationState(
      currentAvatar = Avatar.Vector(Avatars.defaultAvatarsForSelf.keys.first(), Avatars.colors[3], Avatar.DatabaseId.NotSet)
    ),
    onEvent = {}
  )
}
