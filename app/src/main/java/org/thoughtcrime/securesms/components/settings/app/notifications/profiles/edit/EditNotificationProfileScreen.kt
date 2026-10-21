/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.emoji.Emojifier
import org.thoughtcrime.securesms.R

@VisibleForTesting
object EditNotificationProfileTestTags {
  const val LIST = "list"
  const val NAME_FIELD = "name-field"
  const val BUTTON_EMOJI = "button-emoji"
  const val BUTTON_CLEAR = "button-clear"
  const val BUTTON_SAVE = "button-save"
  const val PRESET_PREFIX = "preset-"

  fun preset(index: Int): String = PRESET_PREFIX + index
}

private val presets = listOf(
  Preset("💪", R.string.EditNotificationProfileFragment__work),
  Preset("😴", R.string.EditNotificationProfileFragment__sleep),
  Preset("🚗", R.string.EditNotificationProfileFragment__driving),
  Preset("😊", R.string.EditNotificationProfileFragment__downtime),
  Preset("💡", R.string.EditNotificationProfileFragment__focus)
)

private data class Preset(val emoji: String, @StringRes val name: Int)

/**
 * Names a notification profile and picks its emoji, both when creating one and when editing it from its details.
 *
 * @param emojiPicker The emoji picker shown in a sheet while [EditNotificationProfileState.showEmojiPicker], reporting a
 *   pick through the callback it is given.
 */
@Composable
fun EditNotificationProfileScreen(
  state: EditNotificationProfileState,
  onEvent: (EditNotificationProfileEvent) -> Unit,
  modifier: Modifier = Modifier,
  emojiPicker: @Composable (onEmojiSelected: (String) -> Unit) -> Unit = {}
) {
  val focusRequester = remember { FocusRequester() }
  val systemKeyboard = LocalSoftwareKeyboardController.current

  LaunchedEffect(state.isLoaded) {
    if (state.isLoaded) {
      focusRequester.requestFocus()
      systemKeyboard?.show()
    }
  }

  val title = stringResource(if (state.createMode) R.string.EditNotificationProfileFragment__name_your_profile else R.string.EditNotificationProfileFragment__edit_this_profile)
  val listState = rememberLazyListState()
  val showToolbarTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
  val toolbarTitleAlpha by animateFloatAsState(targetValue = if (showToolbarTitle) 1f else 0f, label = "toolbar-title-alpha")

  Scaffolds.Settings(
    title = title,
    onNavigationClick = { onEvent(EditNotificationProfileEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    titleContent = { _, toolbarTitle ->
      Text(
        text = toolbarTitle,
        style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto),
        modifier = Modifier
          .alpha(toolbarTitleAlpha)
          .then(if (showToolbarTitle) Modifier else Modifier.clearAndSetSemantics {})
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
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier
          .fillMaxSize()
          .testTag(EditNotificationProfileTestTags.LIST)
      ) {
        item(key = "title") {
          Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(hyphens = Hyphens.Auto),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .fillMaxWidth()
              .horizontalGutters()
              .padding(top = 20.dp)
          )
        }

        item(key = "name") {
          NameField(
            state = state,
            onEvent = onEvent,
            focusRequester = focusRequester,
            modifier = Modifier
              .horizontalGutters()
              .padding(top = 24.dp, bottom = 16.dp)
          )
        }

        itemsIndexed(presets, key = { _, preset -> preset.name }) { index, preset ->
          val name = stringResource(preset.name)
          Rows.TextRow(
            text = {
              Text(text = name, style = MaterialTheme.typography.bodyLarge)
            },
            icon = {
              EmojiText(emoji = preset.emoji)
            },
            onClick = { onEvent(EditNotificationProfileEvent.PresetClicked(name = name, emoji = preset.emoji)) },
            modifier = Modifier.testTag(EditNotificationProfileTestTags.preset(index))
          )
        }
      }

      CircularProgressWrapper(
        isLoading = state.isSaving,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
      ) {
        Buttons.LargeTonal(
          onClick = { onEvent(EditNotificationProfileEvent.SaveClicked) },
          enabled = state.isLoaded && !state.isSaving,
          modifier = Modifier.testTag(EditNotificationProfileTestTags.BUTTON_SAVE)
        ) {
          Text(text = stringResource(if (state.createMode) R.string.EditNotificationProfileFragment__create else R.string.EditNotificationProfileFragment__save))
        }
      }
    }
  }

  if (state.showEmojiPicker) {
    BottomSheets.BottomSheet(
      onDismissRequest = { onEvent(EditNotificationProfileEvent.EmojiPickerDismissed) },
      dragHandle = null,
      containerColor = SignalTheme.colors.colorSurface1,
      // The picker keeps itself clear of the navigation bar and keyboard at the bottom.
      contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal) }
    ) {
      emojiPicker { emoji -> onEvent(EditNotificationProfileEvent.EmojiSelected(emoji)) }
    }
  }
}

@Composable
private fun NameField(
  state: EditNotificationProfileState,
  onEvent: (EditNotificationProfileEvent) -> Unit,
  focusRequester: FocusRequester,
  modifier: Modifier = Modifier
) {
  val systemKeyboard = LocalSoftwareKeyboardController.current

  Row(
    verticalAlignment = Alignment.Top,
    modifier = modifier
  ) {
    EmojiButton(
      emoji = state.emoji,
      onClick = {
        systemKeyboard?.hide()
        onEvent(EditNotificationProfileEvent.EmojiButtonClicked)
      },
      modifier = Modifier.padding(top = 4.dp)
    )

    NameTextField(
      state = state,
      onEvent = onEvent,
      modifier = Modifier
        .weight(1f)
        .padding(start = 4.dp)
        .focusRequester(focusRequester)
    )
  }
}

@Composable
private fun EmojiButton(
  emoji: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val chooseEmoji = stringResource(R.string.EditNotificationProfileFragment__choose_emoji)

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
      .clip(CircleShape)
      .clickable(onClick = onClick, role = Role.Button)
      .semantics { contentDescription = chooseEmoji }
      .padding(8.dp)
      .testTag(EditNotificationProfileTestTags.BUTTON_EMOJI)
  ) {
    if (emoji.isNotEmpty()) {
      EmojiText(emoji = emoji)
    } else {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_emoji_plus_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun NameTextField(
  state: EditNotificationProfileState,
  onEvent: (EditNotificationProfileEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val nameLength = state.nameLength

  TextFields.TextField(
    value = state.name,
    onValueChange = { onEvent(EditNotificationProfileEvent.NameChanged(it)) },
    singleLine = true,
    label = { Text(stringResource(R.string.EditNotificationProfileFragment__profile_name)) },
    trailingIcon = if (state.name.isNotEmpty() || state.emoji.isNotEmpty()) {
      {
        IconButton(
          onClick = { onEvent(EditNotificationProfileEvent.ClearClicked) },
          modifier = Modifier.testTag(EditNotificationProfileTestTags.BUTTON_CLEAR)
        ) {
          Icon(imageVector = SignalIcons.X.imageVector, contentDescription = stringResource(R.string.EditNotificationProfileFragment__clear_name_and_emoji))
        }
      }
    } else {
      null
    },
    supportingText = when {
      state.nameError == EditNotificationProfileState.NameError.Empty -> {
        { Text(stringResource(R.string.EditNotificationProfileFragment__profile_must_have_a_name)) }
      }
      state.nameError == EditNotificationProfileState.NameError.Duplicate -> {
        { Text(stringResource(R.string.EditNotificationProfileFragment__a_profile_with_this_name_already_exists)) }
      }
      nameLength >= EditNotificationProfileState.NAME_COUNT_DISPLAY_THRESHOLD -> {
        { Text(stringResource(R.string.EditNotificationProfileFragment__count, nameLength, EditNotificationProfileState.NAME_MAX_GRAPHEMES)) }
      }
      else -> null
    },
    isError = state.nameError != EditNotificationProfileState.NameError.None,
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
    keyboardActions = KeyboardActions(onDone = { onEvent(EditNotificationProfileEvent.SaveClicked) }),
    modifier = modifier
      .fillMaxWidth()
      .testTag(EditNotificationProfileTestTags.NAME_FIELD)
  )
}

@Composable
private fun EmojiText(emoji: String) {
  ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)) {
    Emojifier(text = emoji) { annotatedText, inlineContent ->
      Text(text = annotatedText, inlineContent = inlineContent)
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditNotificationProfileScreenCreatePreview() {
  EditNotificationProfileScreen(
    state = EditNotificationProfileState(createMode = true, isLoaded = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditNotificationProfileScreenEditPreview() {
  EditNotificationProfileScreen(
    state = EditNotificationProfileState(createMode = false, isLoaded = true, name = "Work", emoji = "💪", nameError = EditNotificationProfileState.NameError.Duplicate),
    onEvent = {}
  )
}
