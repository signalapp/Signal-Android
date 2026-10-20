/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.appearance.appicon

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.R as CoreUiR

private const val COLUMN_COUNT = 4

@VisibleForTesting
object AppIconSelectionTestTags {
  const val SCROLLER = "scroller"
  const val LEARN_MORE = "learn-more"
  const val ICON_PREFIX = "icon-"
  const val DIALOG_CONFIRM = "dialog-confirm"
  const val DIALOG_CANCEL = "dialog-cancel"

  fun icon(preset: AppIconPreset): String = ICON_PREFIX + preset.name
}

/**
 * Screen allowing the user to view all the possible icons and select a new one to use.
 */
@Composable
fun AppIconSelectionScreen(
  state: AppIconSelectionState,
  onEvent: (AppIconSelectionEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__app_icon),
    onNavigationClick = { onEvent(AppIconSelectionEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(CoreUiR.string.CloseButton__content_description),
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .testTag(AppIconSelectionTestTags.SCROLLER)
    ) {
      Spacer(modifier = Modifier.size(12.dp))
      CaveatWarning(
        onClick = { onEvent(AppIconSelectionEvent.LearnMoreClicked) },
        modifier = Modifier.padding(horizontal = 24.dp)
      )
      Spacer(modifier = Modifier.size(12.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        AppIconPreset.entries.chunked(COLUMN_COUNT).forEach { presets ->
          IconRow(
            presets = presets,
            selectedIcon = state.selectedIcon,
            onEvent = onEvent
          )
        }
      }
    }
  }

  when (val dialog = state.dialog) {
    AppIconSelectionState.Dialog.None -> Unit
    is AppIconSelectionState.Dialog.ConfirmIconChange -> ChangeIconDialog(dialog.preset, onEvent)
  }
}

@Composable
private fun ChangeIconDialog(pendingIcon: AppIconPreset, onEvent: (AppIconSelectionEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(AppIconSelectionEvent.DialogDismissed) },
    confirmButton = {
      TextButton(
        onClick = { onEvent(AppIconSelectionEvent.ChangeConfirmed) },
        modifier = Modifier.testTag(AppIconSelectionTestTags.DIALOG_CONFIRM)
      ) {
        Text(text = stringResource(R.string.preferences__app_icon_dialog_ok))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onEvent(AppIconSelectionEvent.DialogDismissed) },
        modifier = Modifier.testTag(AppIconSelectionTestTags.DIALOG_CANCEL)
      ) {
        Text(text = stringResource(R.string.preferences__app_icon_dialog_cancel))
      }
    },
    icon = {
      AppIcon(preset = pendingIcon, isSelected = false, onClick = {})
    },
    title = {
      Text(
        text = stringResource(R.string.preferences__app_icon_dialog_title, presetLabel(pendingIcon)),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineSmall
      )
    },
    text = {
      Text(
        text = stringResource(R.string.preferences__app_icon_dialog_description),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium
      )
    }
  )
}

/**
 * One row of icons that the user may choose from.
 */
@Composable
private fun IconRow(presets: List<AppIconPreset>, selectedIcon: AppIconPreset, onEvent: (AppIconSelectionEvent) -> Unit) {
  Row(modifier = Modifier.fillMaxWidth()) {
    presets.forEach { preset ->
      IconGridElement(
        preset = preset,
        isSelected = preset == selectedIcon,
        onClick = { onEvent(AppIconSelectionEvent.IconClicked(preset)) },
        modifier = Modifier
          .padding(vertical = 18.dp)
          .weight(1f)
          .testTag(AppIconSelectionTestTags.icon(preset))
      )
    }
  }
}

/**
 * An individual icon inside the grid, including the border around the selected icon.
 */
@Composable
private fun IconGridElement(preset: AppIconPreset, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val boxModifier = Modifier.size(64.dp)
    Box(
      modifier = if (isSelected) boxModifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else boxModifier
    ) {
      AppIcon(preset = preset, isSelected = isSelected, onClick = onClick, modifier = Modifier.align(Alignment.Center))
    }
    Spacer(modifier = Modifier.size(8.dp))
    Text(
      text = presetLabel(preset),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/**
 * The icon itself, which shrinks when selected so the border fits around it.
 */
@Composable
private fun AppIcon(preset: AppIconPreset, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val bitmapSize by animateFloatAsState(
    targetValue = if (isSelected) 48f else 64f,
    label = "Icon Size",
    animationSpec = tween(durationMillis = 150, easing = CubicBezierEasing(0.17f, 0.17f, 0f, 1f))
  )

  Image(
    painter = painterResource(preset.iconPreviewResId),
    contentDescription = presetLabel(preset),
    modifier = modifier
      .size(bitmapSize.dp)
      .graphicsLayer(
        shape = CircleShape,
        shadowElevation = if (isSelected) 4f else 8f,
        clip = true
      )
      .clickable(onClick = onClick)
  )
}

/**
 * The caveat explaining where the icon shows up, ending in a "learn more" call to action. The whole block is clickable.
 */
@Composable
private fun CaveatWarning(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val learnMoreString = stringResource(R.string.preferences__app_icon_learn_more)
  val completeString = stringResource(R.string.preferences__app_icon_warning_learn_more)
  val learnMoreStartIndex = completeString.indexOf(learnMoreString).coerceAtLeast(0)
  val learnMoreEndIndex = learnMoreStartIndex + learnMoreString.length

  val annotatedText = buildAnnotatedString {
    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
      append(completeString.substring(0, learnMoreStartIndex))
    }
    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
      append(learnMoreString)
    }
    if (learnMoreEndIndex < completeString.length) {
      append(completeString.substring(learnMoreEndIndex))
    }
  }

  Text(
    text = annotatedText,
    style = MaterialTheme.typography.bodyMedium,
    modifier = modifier
      .clickable(onClick = onClick)
      .testTag(AppIconSelectionTestTags.LEARN_MORE)
  )
}

@Composable
private fun presetLabel(preset: AppIconPreset): String {
  return stringResource(preset.labelResId ?: LocalContext.current.applicationInfo.labelRes)
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppIconSelectionScreenPreview() {
  AppIconSelectionScreen(
    state = AppIconSelectionState(currentIcon = AppIconPreset.DEFAULT),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun AppIconSelectionScreenConfirmPreview() {
  AppIconSelectionScreen(
    state = AppIconSelectionState(
      currentIcon = AppIconPreset.DEFAULT,
      dialog = AppIconSelectionState.Dialog.ConfirmIconChange(AppIconPreset.WEATHER)
    ),
    onEvent = {}
  )
}
