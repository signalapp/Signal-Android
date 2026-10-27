/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernameedit

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import org.signal.appsettings.ProgressTonalButton
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.requestFocusWhenReady
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.util.SpanUtil
import org.signal.core.util.UsernameUtil

@VisibleForTesting
object UsernameEditTestTags {
  const val SCROLLER = "scroller"
  const val USERNAME = "username"
  const val NICKNAME_INPUT = "nickname-input"
  const val DISCRIMINATOR_INPUT = "discriminator-input"
  const val RESERVING = "reserving"
  const val ERROR = "error"
  const val BUTTON_SUBMIT = "button-submit"
  const val BUTTON_DELETE = "button-delete"
}

/**
 * Lets the user choose, change, or delete their username. A username is a nickname and a number, written
 * `nickname.number`. The number is reserved for the user as they type, unless they choose their own.
 */
@Composable
fun UsernameEditScreen(
  state: UsernameEditState,
  onEvent: (UsernameEditEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val nicknameFocusRequester = remember { FocusRequester() }
  val discriminatorFocusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    nicknameFocusRequester.requestFocusWhenReady()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.UsernameEditFragment_username),
    onNavigationClick = { onEvent(UsernameEditEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(UsernameEditEvent.SnackbarDismissed) }
      )
    },
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .imePadding()
        .appSettingsScreen(),
      horizontalAlignment = Alignment.End
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .testTag(UsernameEditTestTags.SCROLLER)
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .padding(top = 32.dp)
            .size(64.dp)
            .background(color = SignalTheme.colors.colorSurface2, shape = CircleShape)
        ) {
          Icon(imageVector = SignalIcons.At.imageVector, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }

        Text(
          text = state.username ?: stringResource(R.string.UsernameEditFragment__choose_your_username),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .horizontalGutters()
            .padding(top = 12.dp)
            .testTag(UsernameEditTestTags.USERNAME)
        )

        UsernameField(
          state = state,
          onEvent = onEvent,
          nicknameFocusRequester = nicknameFocusRequester,
          discriminatorFocusRequester = discriminatorFocusRequester,
          modifier = Modifier
            .fillMaxWidth()
            .horizontalGutters()
            .padding(top = 24.dp)
        )

        val learnMore = stringResource(R.string.UsernameEditScreen__learn_more)
        val description = stringResource(R.string.UsernameEditScreen__usernames_let_others_message, learnMore)
        Texts.LinkifiedText(
          textWithUrlSpans = SpanUtil.urlSubsequence(description, learnMore, stringResource(R.string.username_support_url)),
          onUrlClick = { url -> onEvent(UsernameEditEvent.LearnMoreClicked(url)) },
          style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
          modifier = Modifier
            .fillMaxWidth()
            .horizontalGutters()
            .padding(top = 16.dp, bottom = 24.dp)
        )
      }

      when (state.button) {
        UsernameEditState.Button.SUBMIT, UsernameEditState.Button.SUBMIT_DISABLED, UsernameEditState.Button.SUBMIT_LOADING -> {
          ProgressTonalButton(
            text = stringResource(R.string.UsernameEditScreen__save),
            onClick = { onEvent(UsernameEditEvent.SubmitClicked) },
            inProgress = state.button == UsernameEditState.Button.SUBMIT_LOADING,
            enabled = state.button == UsernameEditState.Button.SUBMIT,
            modifier = Modifier
              .padding(16.dp)
              .testTag(UsernameEditTestTags.BUTTON_SUBMIT)
          )
        }

        UsernameEditState.Button.DELETE, UsernameEditState.Button.DELETE_DISABLED, UsernameEditState.Button.DELETE_LOADING -> {
          ProgressTonalButton(
            text = stringResource(R.string.UsernameEditFragment_delete),
            onClick = { onEvent(UsernameEditEvent.DeleteClicked) },
            inProgress = state.button == UsernameEditState.Button.DELETE_LOADING,
            enabled = state.button == UsernameEditState.Button.DELETE,
            modifier = Modifier
              .padding(16.dp)
              .testTag(UsernameEditTestTags.BUTTON_DELETE)
          )
        }
      }
    }
  }

  when (state.dialog) {
    UsernameEditState.Dialog.None -> Unit

    UsernameEditState.Dialog.ConfirmReset -> {
      Dialogs.SimpleAlertDialog(
        title = "",
        body = stringResource(R.string.UsernameEditFragment_change_confirmation_message),
        confirm = stringResource(R.string.UsernameEditFragment_continue),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(UsernameEditEvent.ResetConfirmed) },
        onDeny = { onEvent(UsernameEditEvent.DialogDismissed) },
        onDismissRequest = { onEvent(UsernameEditEvent.DialogDismissed) }
      )
    }

    UsernameEditState.Dialog.ConfirmSameUsernameRecovery -> {
      Dialogs.SimpleAlertDialog(
        title = "",
        body = stringResource(R.string.UsernameEditFragment_recovery_dialog_confirmation),
        confirm = stringResource(android.R.string.ok),
        dismiss = stringResource(android.R.string.cancel),
        onConfirm = { onEvent(UsernameEditEvent.SameUsernameRecoveryConfirmed) },
        onDeny = { onEvent(UsernameEditEvent.DialogDismissed) },
        onDismissRequest = { onEvent(UsernameEditEvent.DialogDismissed) }
      )
    }
  }
}

/**
 * The nickname field, with the number after it in the same box. The number only shows once there is one, and a spinner
 * stands in front of it while one is being reserved. The label always stays small at the top of the field, so that the
 * number, which sits in the suffix, is never hidden behind it.
 *
 * The field keeps its own [TextFieldState] for editing, and stays in step with [UsernameEditState.nickname] both ways.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsernameField(
  state: UsernameEditState,
  onEvent: (UsernameEditEvent) -> Unit,
  nicknameFocusRequester: FocusRequester,
  discriminatorFocusRequester: FocusRequester,
  modifier: Modifier = Modifier
) {
  val error = state.error
  val nickname = rememberTextFieldState(state.nickname)
  val currentOnEvent by rememberUpdatedState(onEvent)

  val sync = remember { NicknameSync(fieldText = state.nickname) }

  LaunchedEffect(nickname) {
    snapshotFlow { nickname.text.toString() }
      .filter { it != sync.fieldText }
      .collect {
        sync.fieldText = it
        sync.unacknowledged.addLast(it)
        currentOnEvent(UsernameEditEvent.NicknameChanged(it))
      }
  }

  LaunchedEffect(state.nickname) {
    val echoed = sync.unacknowledged.indexOf(state.nickname)
    if (echoed >= 0) {
      repeat(echoed + 1) { sync.unacknowledged.removeFirst() }
    } else if (state.nickname != sync.fieldText) {
      sync.fieldText = state.nickname
      nickname.setTextAndPlaceCursorAtEnd(state.nickname)
    }
  }

  TextField(
    state = nickname,
    enabled = state.inputEnabled,
    lineLimits = TextFieldLineLimits.SingleLine,
    labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
    label = { Text(stringResource(R.string.UsernameEditFragment_username)) },
    suffix = if (state.showDiscriminator || state.reserving) {
      {
        Discriminator(
          state = state,
          onEvent = onEvent,
          focusRequester = discriminatorFocusRequester
        )
      }
    } else {
      null
    },
    supportingText = if (error != null) {
      {
        Text(
          text = error.message(),
          modifier = Modifier.testTag(UsernameEditTestTags.ERROR)
        )
      }
    } else {
      null
    },
    isError = error != null,
    keyboardOptions = KeyboardOptions(imeAction = if (state.showDiscriminator) ImeAction.Next else ImeAction.Done),
    onKeyboardAction = {
      if (state.showDiscriminator) {
        discriminatorFocusRequester.requestFocus()
      } else {
        onEvent(UsernameEditEvent.SubmitClicked)
      }
    },
    modifier = modifier
      .focusRequester(nicknameFocusRequester)
      .testTag(UsernameEditTestTags.NICKNAME_INPUT)
  )
}

/**
 * Keeps [UsernameField] and [UsernameEditState.nickname] in step. State lags behind fast typing, so a nickname the field
 * reported comes back as an echo, possibly after newer ones: those are acknowledged rather than pushed into the field,
 * and only a nickname the view model set itself is.
 */
private class NicknameSync(var fieldText: String) {
  /** Nicknames the field reported that the view model hasn't echoed back yet, oldest first. */
  val unacknowledged = ArrayDeque<String>()
}

@Composable
private fun Discriminator(
  state: UsernameEditState,
  onEvent: (UsernameEditEvent) -> Unit,
  focusRequester: FocusRequester
) {
  val textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).copy(color = MaterialTheme.colorScheme.onSurface)

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.height(IntrinsicSize.Min)
  ) {
    if (state.reserving) {
      CircularProgressIndicator(
        strokeWidth = 2.dp,
        modifier = Modifier
          .padding(end = 8.dp)
          .size(16.dp)
          .testTag(UsernameEditTestTags.RESERVING)
      )
    }

    if (state.showDiscriminator) {
      VerticalDivider(
        modifier = Modifier
          .fillMaxHeight()
          .padding(vertical = 4.dp)
      )

      BasicTextField(
        value = state.discriminator,
        onValueChange = { onEvent(UsernameEditEvent.DiscriminatorChanged(it)) },
        enabled = state.inputEnabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onEvent(UsernameEditEvent.SubmitClicked) }),
        decorationBox = { innerTextField ->
          Box {
            if (state.discriminator.isEmpty()) {
              Text(text = stringResource(R.string.UsernameEditFragment__00), style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            innerTextField()
          }
        },
        modifier = Modifier
          .padding(start = 12.dp)
          .width(IntrinsicSize.Min)
          .widthIn(min = 24.dp)
          .focusRequester(focusRequester)
          .testTag(UsernameEditTestTags.DISCRIMINATOR_INPUT)
      )
    }
  }
}

@Composable
private fun UsernameEditState.UsernameError.message(): String {
  return when (this) {
    UsernameEditState.UsernameError.TOO_SHORT, UsernameEditState.UsernameError.TOO_LONG -> stringResource(R.string.UsernameEditFragment_usernames_must_be_between_a_and_b_characters, UsernameUtil.MIN_NICKNAME_LENGTH, UsernameUtil.MAX_NICKNAME_LENGTH)
    UsernameEditState.UsernameError.INVALID_CHARACTERS -> stringResource(R.string.UsernameEditFragment_usernames_can_only_include)
    UsernameEditState.UsernameError.CANNOT_START_WITH_NUMBER -> stringResource(R.string.UsernameEditFragment_usernames_cannot_begin_with_a_number)
    UsernameEditState.UsernameError.INVALID_GENERIC -> stringResource(R.string.UsernameEditFragment_username_is_invalid)
    UsernameEditState.UsernameError.TAKEN -> stringResource(R.string.UsernameEditFragment_this_username_is_taken)
    UsernameEditState.UsernameError.DISCRIMINATOR_HAS_INVALID_CHARACTERS, UsernameEditState.UsernameError.DISCRIMINATOR_NOT_AVAILABLE -> stringResource(R.string.UsernameEditFragment__this_username_is_not_available_try_another_number)
    UsernameEditState.UsernameError.DISCRIMINATOR_TOO_LONG -> stringResource(R.string.UsernameEditFragment__invalid_username_enter_a_maximum_of_d_digits, UsernameUtil.MAX_DISCRIMINATOR_LENGTH)
    UsernameEditState.UsernameError.DISCRIMINATOR_TOO_SHORT -> stringResource(R.string.UsernameEditFragment__invalid_username_enter_a_minimum_of_d_digits, UsernameUtil.MIN_DISCRIMINATOR_LENGTH)
    UsernameEditState.UsernameError.DISCRIMINATOR_CANNOT_BE_00 -> stringResource(R.string.UsernameEditFragment__this_number_cant_be_00)
    UsernameEditState.UsernameError.DISCRIMINATOR_CANNOT_START_WITH_0 -> stringResource(R.string.UsernameEditFragment__this_number_cant_start_with_0)
  }
}

@Composable
private fun UsernameEditState.Snackbar.message(): String? {
  return when (this) {
    UsernameEditState.Snackbar.None -> null
    UsernameEditState.Snackbar.Taken -> stringResource(R.string.UsernameEditFragment_this_username_is_taken)
    UsernameEditState.Snackbar.Invalid -> stringResource(R.string.UsernameEditFragment_username_is_invalid)
    UsernameEditState.Snackbar.NetworkFailure -> stringResource(R.string.UsernameEditFragment_encountered_a_network_error)
    UsernameEditState.Snackbar.RateLimited -> stringResource(R.string.UsernameEditFragment_rate_limit_exceeded_error)
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameEditScreenNoUsernamePreview() {
  UsernameEditScreen(state = UsernameEditState(), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameEditScreenReservedPreview() {
  UsernameEditScreen(
    state = UsernameEditState(nickname = "spiderman", discriminator = "42", showDiscriminator = true, username = "spiderman.42", button = UsernameEditState.Button.SUBMIT),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameEditScreenReservingPreview() {
  UsernameEditScreen(
    state = UsernameEditState(nickname = "spiderman", reserving = true),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameEditScreenErrorPreview() {
  UsernameEditScreen(
    state = UsernameEditState(nickname = "1spiderman", error = UsernameEditState.UsernameError.CANNOT_START_WITH_NUMBER),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameEditScreenDeletePreview() {
  UsernameEditScreen(
    state = UsernameEditState(showDiscriminator = true, discriminator = "42", button = UsernameEditState.Button.DELETE),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun UsernameEditScreenConfirmResetPreview() {
  UsernameEditScreen(
    state = UsernameEditState(nickname = "spiderman", discriminator = "42", showDiscriminator = true, username = "spiderman.42", button = UsernameEditState.Button.SUBMIT, dialog = UsernameEditState.Dialog.ConfirmReset),
    onEvent = {}
  )
}
