/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernamecolorpicker

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.usernamelinks.QrCodeBadge
import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

@VisibleForTesting
object UsernameLinkQrColorPickerTestTags {
  const val SCROLLER = "scroller"
  const val BUTTON_DONE = "button-done"
  const val COLOR_PREFIX = "color-"

  fun color(colorScheme: UsernameQrCodeColorScheme): String = COLOR_PREFIX + colorScheme.serialize()
}

/**
 * Gives the user the ability to change the color of their shareable username QR code with a live preview.
 */
@Composable
fun UsernameLinkQrColorPickerScreen(
  state: UsernameLinkQrColorPickerState,
  onEvent: (UsernameLinkQrColorPickerEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.UsernameLinkSettings_color_picker_app_bar_title),
    onNavigationClick = { onEvent(UsernameLinkQrColorPickerEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .fillMaxWidth()
        .fillMaxHeight()
        .testTag(UsernameLinkQrColorPickerTestTags.SCROLLER),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      QrCodeBadge(
        data = state.qrCodeData,
        colorScheme = state.selectedColorScheme,
        username = state.username,
        modifier = Modifier.padding(horizontal = 58.dp, vertical = 24.dp)
      )

      ColorPicker(
        selected = state.selectedColorScheme,
        onEvent = onEvent
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.End
      ) {
        Buttons.MediumTonal(
          onClick = { onEvent(UsernameLinkQrColorPickerEvent.DoneClicked) },
          modifier = Modifier.testTag(UsernameLinkQrColorPickerTestTags.BUTTON_DONE)
        ) {
          Text(stringResource(R.string.UsernameLinkSettings_done_button_label))
        }
      }
    }
  }
}

@Composable
private fun ColorPicker(selected: UsernameQrCodeColorScheme, onEvent: (UsernameLinkQrColorPickerEvent) -> Unit) {
  LazyVerticalGrid(
    modifier = Modifier
      .padding(horizontal = 30.dp)
      .heightIn(max = 880.dp),
    columns = GridCells.Adaptive(minSize = 88.dp)
  ) {
    items(UsernameQrCodeColorScheme.entries, key = { it.serialize() }) { colorScheme ->
      ColorPickerItem(
        colorScheme = colorScheme,
        selected = colorScheme == selected,
        onClick = { onEvent(UsernameLinkQrColorPickerEvent.ColorSelected(colorScheme)) },
        modifier = Modifier.testTag(UsernameLinkQrColorPickerTestTags.color(colorScheme))
      )
    }
  }
}

@Composable
private fun ColorPickerItem(colorScheme: UsernameQrCodeColorScheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val outerBorderColor by animateColorAsState(targetValue = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent)
  val colorCircleSize by animateFloatAsState(targetValue = if (selected) 44f else 56f)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Column(
      modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 13.dp)
        .border(width = 2.dp, color = outerBorderColor, shape = CircleShape)
        .size(56.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Surface(
        onClick = onClick,
        modifier = Modifier
          .border(width = 2.dp, color = Color.Black.copy(alpha = 0.12f), shape = CircleShape)
          .size(colorCircleSize.dp),
        shape = CircleShape,
        color = colorScheme.borderColor,
        content = {}
      )
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameLinkQrColorPickerScreenPreview() {
  UsernameLinkQrColorPickerScreen(
    state = UsernameLinkQrColorPickerState(
      username = "parker.42",
      qrCodeData = QrCodeState.Present(QrCodeData.forData("https://signal.me/#eu/asdfasdfasdfasdfasdfasdfasdfasdfasdf")),
      selectedColorScheme = UsernameQrCodeColorScheme.Orange
    ),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameLinkQrColorPickerScreenLoadingPreview() {
  UsernameLinkQrColorPickerScreen(state = UsernameLinkQrColorPickerState(username = "parker.42"), onEvent = {})
}
