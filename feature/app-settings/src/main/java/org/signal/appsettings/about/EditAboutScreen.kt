/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.about

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.appsettings.AppSettingsLazyColumn
import org.signal.appsettings.ProgressTonalButton
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.emoji.EmojiButton
import org.signal.emoji.EmojiPickerSheet
import org.signal.emoji.EmojiText
import org.signal.emoji.EmojiTextField
import org.signal.emoji.requestFocusWhenReady

@VisibleForTesting
object EditAboutTestTags {
  const val LIST = "list"
  const val ABOUT_INPUT = "about-input"
  const val BUTTON_EMOJI = "button-emoji"
  const val BUTTON_CLEAR = "button-clear"
  const val BUTTON_SAVE = "button-save"
  const val COUNT = "count"

  fun preset(index: Int): String = "preset-$index"
}

private data class Preset(val emoji: String, @StringRes val about: Int)

private val presets = listOf(
  Preset("👋", R.string.EditAboutFragment_speak_freely),
  Preset("🤐", R.string.EditAboutFragment_encrypted),
  Preset("🙏", R.string.EditAboutFragment_be_kind),
  Preset("☕", R.string.EditAboutFragment_coffee_lover),
  Preset("👍", R.string.EditAboutFragment_free_to_chat),
  Preset("📵", R.string.EditAboutFragment_taking_a_break),
  Preset("🚀", R.string.EditAboutFragment_working_on_something_new)
)

/**
 * Lets the user change the about text and emoji on their profile, or pick one of a few presets.
 *
 * @param emojiPicker The emoji picker shown in a sheet while [EditAboutState.showEmojiPicker], reporting a pick through
 *   the callback it is given.
 */
@Composable
fun EditAboutScreen(
  state: EditAboutState,
  onEvent: (EditAboutEvent) -> Unit,
  modifier: Modifier = Modifier,
  emojiPicker: @Composable (onEmojiSelected: (String) -> Unit) -> Unit = {}
) {
  val focusRequester = remember { FocusRequester() }

  // Focuses the field on the way in, and again once a picked emoji brings the user back from the picker.
  LaunchedEffect(state.emoji) {
    focusRequester.requestFocusWhenReady()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.EditAboutFragment_about),
    onNavigationClick = { onEvent(EditAboutEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(EditAboutEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .imePadding()
        .fillMaxSize()
    ) {
      AppSettingsLazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.testTag(EditAboutTestTags.LIST)
      ) {
        item(key = "about") {
          AboutField(
            state = state,
            onEvent = onEvent,
            focusRequester = focusRequester,
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
          )
        }

        itemsIndexed(presets, key = { _, preset -> preset.about }) { index, preset ->
          val about = stringResource(preset.about)
          Rows.TextRow(
            text = { Text(text = about, style = MaterialTheme.typography.bodyLarge) },
            icon = { EmojiText(text = preset.emoji, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)) },
            onClick = { onEvent(EditAboutEvent.PresetClicked(emoji = preset.emoji, about = about)) },
            modifier = Modifier.testTag(EditAboutTestTags.preset(index))
          )
        }
      }

      Box(
        contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .appSettingsScreen()
      ) {
        ProgressTonalButton(
          text = stringResource(R.string.EditAboutScreen__save),
          onClick = { onEvent(EditAboutEvent.SaveClicked) },
          inProgress = state.saving,
          modifier = Modifier
            .padding(16.dp)
            .testTag(EditAboutTestTags.BUTTON_SAVE)
        )
      }
    }
  }

  if (state.showEmojiPicker) {
    EmojiPickerSheet(onDismissRequest = { onEvent(EditAboutEvent.EmojiPickerDismissed) }) {
      emojiPicker { emoji -> onEvent(EditAboutEvent.EmojiSelected(emoji)) }
    }
  }
}

@Composable
private fun AboutField(
  state: EditAboutState,
  onEvent: (EditAboutEvent) -> Unit,
  focusRequester: FocusRequester,
  modifier: Modifier = Modifier
) {
  val keyboard = LocalSoftwareKeyboardController.current
  val interactionSource = remember { MutableInteractionSource() }
  val aboutLength = state.aboutLength

  Row(
    verticalAlignment = Alignment.Top,
    modifier = modifier.fillMaxWidth()
  ) {
    EmojiButton(
      emoji = state.emoji,
      contentDescription = stringResource(R.string.EditAboutScreen__choose_emoji),
      onClick = {
        keyboard?.hide()
        onEvent(EditAboutEvent.EmojiButtonClicked)
      },
      modifier = Modifier
        .padding(top = 4.dp)
        .testTag(EditAboutTestTags.BUTTON_EMOJI)
    )

    EmojiTextField(
      value = state.about,
      onValueChange = { onEvent(EditAboutEvent.AboutChanged(it)) },
      enabled = !state.saving,
      singleLine = true,
      placeholder = { Text(stringResource(R.string.EditAboutFragment_write_a_few_words_about_yourself)) },
      trailingIcon = {
        IconButton(
          onClick = { onEvent(EditAboutEvent.ClearClicked) },
          enabled = !state.saving,
          modifier = Modifier.testTag(EditAboutTestTags.BUTTON_CLEAR)
        ) {
          Icon(imageVector = SignalIcons.X.imageVector, contentDescription = stringResource(R.string.EditAboutScreen__clear))
        }
      },
      supportingText = if (aboutLength >= EditAboutState.ABOUT_COUNT_DISPLAY_THRESHOLD) {
        {
          Text(
            text = stringResource(R.string.EditAboutFragment_count, aboutLength, EditAboutState.ABOUT_MAX_GRAPHEMES),
            modifier = Modifier.testTag(EditAboutTestTags.COUNT)
          )
        }
      } else {
        null
      },
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Done,
      onImeAction = { onEvent(EditAboutEvent.SaveClicked) },
      focusRequester = focusRequester,
      interactionSource = interactionSource,
      modifier = Modifier
        .weight(1f)
        .padding(start = 4.dp)
        .testTag(EditAboutTestTags.ABOUT_INPUT)
    )
  }
}

@Composable
private fun EditAboutState.Snackbar.message(): String? {
  return when (this) {
    EditAboutState.Snackbar.None -> null
    EditAboutState.Snackbar.NetworkFailure -> stringResource(R.string.EditAboutScreen__failed_to_save_due_to_network_issues)
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditAboutScreenPreview() {
  EditAboutScreen(
    state = EditAboutState(about = "Speak freely", emoji = "👋"),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditAboutScreenEmptyPreview() {
  EditAboutScreen(
    state = EditAboutState(),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditAboutScreenNearLimitPreview() {
  EditAboutScreen(
    state = EditAboutState(about = "a".repeat(EditAboutState.ABOUT_COUNT_DISPLAY_THRESHOLD + 5), emoji = "☕"),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditAboutScreenSavingPreview() {
  EditAboutScreen(
    state = EditAboutState(about = "Speak freely", emoji = "👋", saving = true),
    onEvent = {}
  )
}
