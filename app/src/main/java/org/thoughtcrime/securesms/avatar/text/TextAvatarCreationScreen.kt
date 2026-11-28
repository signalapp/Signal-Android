/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.text

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.appSettingsScreen
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.TitledTabRow
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.requestFocusWhenReady
import org.signal.emoji.EmojiTextInputParams
import org.signal.emoji.LocalEmojiTextInput
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars
import org.thoughtcrime.securesms.avatar.compose.AvatarColorGrid
import org.thoughtcrime.securesms.avatar.compose.rememberAvatarFontSize
import org.thoughtcrime.securesms.avatar.compose.rememberAvatarTypeface

/** How many characters, counting each emoji as one, fit in a text avatar. */
private const val MAX_CHARACTERS = 3

@VisibleForTesting
object TextAvatarCreationTestTags {
  const val TEXT_INPUT = "text-input"
  const val DONE_BUTTON = "done-button"
  const val SCROLL_CONTAINER = "scroll-container"
}

/**
 * Creates an avatar from a few characters of text, typed straight into the avatar, then colored.
 */
@Composable
fun TextAvatarCreationScreen(
  state: TextAvatarCreationState,
  onEvent: (TextAvatarCreationEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current

  LaunchedEffect(state.selectedTab) {
    when (state.selectedTab) {
      TextAvatarCreationState.Tab.TEXT -> focusRequester.requestFocusWhenReady()
      TextAvatarCreationState.Tab.COLOR -> {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
      }
    }
  }

  Scaffolds.Settings(
    title = stringResource(R.string.TextAvatarCreationFragment__preview),
    onNavigationClick = { onEvent(TextAvatarCreationEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    bottomBar = {
      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
          .padding(end = 16.dp, bottom = 16.dp)
      ) {
        Buttons.LargePrimary(
          onClick = { onEvent(TextAvatarCreationEvent.DoneClicked) },
          modifier = Modifier.testTag(TextAvatarCreationTestTags.DONE_BUTTON)
        ) {
          Text(text = stringResource(R.string.TextAvatarCreationFragment__done))
        }
      }
    },
    expandableTitle = true,
    modifier = modifier
  ) { contentPadding ->
    val avatarBringIntoView = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    // The text field is a View, which Compose doesn't scroll into view on its own.
    LaunchedEffect(imeBottom, state.selectedTab) {
      if (imeBottom > 0 && state.selectedTab == TextAvatarCreationState.Tab.TEXT) {
        avatarBringIntoView.bringIntoView()
      }
    }

    Column(
      modifier = Modifier
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
        .appSettingsScreen()
        .testTag(TextAvatarCreationTestTags.SCROLL_CONTAINER)
    ) {
      val tabs = TextAvatarCreationState.Tab.entries

      TitledTabRow(
        tabTitles = tabs.map { stringResource(it.titleRes) },
        selectedTabIndex = tabs.indexOf(state.selectedTab),
        onTabClick = { onEvent(TextAvatarCreationEvent.TabClicked(tabs[it])) }
      )

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp)
          .bringIntoViewRequester(avatarBringIntoView)
      ) {
        AvatarTextInput(
          avatar = state.currentAvatar,
          enabled = state.selectedTab == TextAvatarCreationState.Tab.TEXT,
          focusRequester = focusRequester,
          onEvent = onEvent,
          modifier = Modifier
            .padding(top = 24.dp)
            .size(dimensionResource(R.dimen.avatar_picker_image_width))
            .clip(CircleShape)
        )
      }

      AnimatedVisibility(
        visible = state.selectedTab == TextAvatarCreationState.Tab.COLOR,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
      ) {
        AvatarColorGrid(
          selected = state.currentAvatar.color,
          onColorSelected = { onEvent(TextAvatarCreationEvent.ColorSelected(it)) },
          modifier = Modifier
            .horizontalGutters()
            .padding(bottom = 24.dp)
        )
      }
    }
  }
}

/**
 * The avatar itself, with its text editable in place and sized to fill it, as the avatar will draw it.
 */
@Composable
private fun AvatarTextInput(
  avatar: Avatar.Text,
  enabled: Boolean,
  focusRequester: FocusRequester,
  onEvent: (TextAvatarCreationEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val input = LocalEmojiTextInput.current
  val typeface = rememberAvatarTypeface()
  val interactionSource = remember { MutableInteractionSource() }
  val foreground = Color(avatar.color.foregroundColor)

  BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier = modifier.background(Color(avatar.color.backgroundColor))
  ) {
    val fontSize = rememberAvatarFontSize(avatar.text, constraints)

    input.Content(
      params = EmojiTextInputParams(
        value = avatar.text,
        onValueChange = { onEvent(TextAvatarCreationEvent.TextChanged(it)) },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = foreground, fontSize = fontSize, fontFamily = typeface, textAlign = TextAlign.Center),
        cursorColor = foreground,
        capitalization = KeyboardCapitalization.Characters,
        imeAction = ImeAction.Next,
        onImeAction = { onEvent(TextAvatarCreationEvent.KeyboardNextClicked) },
        autofillHint = null,
        focusRequester = focusRequester,
        interactionSource = interactionSource,
        jumboEmoji = true,
        maxCharacters = MAX_CHARACTERS
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag(TextAvatarCreationTestTags.TEXT_INPUT)
    )
  }
}

private val TextAvatarCreationState.Tab.titleRes: Int
  get() = when (this) {
    TextAvatarCreationState.Tab.TEXT -> R.string.TextAvatarCreationFragment__text
    TextAvatarCreationState.Tab.COLOR -> R.string.TextAvatarCreationFragment__color
  }

@PreviewWrapper(SignalPreviewWrapper::class)
@AllDevicePreviews
@Composable
private fun TextAvatarCreationScreenTextPreview() {
  TextAvatarCreationScreen(
    state = TextAvatarCreationState(Avatar.Text("PP", Avatars.colors[3], Avatar.DatabaseId.NotSet)),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@AllDevicePreviews
@Composable
private fun TextAvatarCreationScreenColorPreview() {
  TextAvatarCreationScreen(
    state = TextAvatarCreationState(
      currentAvatar = Avatar.Text("PP", Avatars.colors[3], Avatar.DatabaseId.NotSet),
      selectedTab = TextAvatarCreationState.Tab.COLOR
    ),
    onEvent = {}
  )
}
