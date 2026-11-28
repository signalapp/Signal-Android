/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.profilename

import androidx.annotation.VisibleForTesting
import androidx.autofill.HintConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.ProgressTonalButton
import org.signal.appsettings.R
import org.signal.appsettings.appSettingsScreen
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.requestFocusWhenReady
import org.signal.emoji.EmojiTextField

@VisibleForTesting
object EditProfileNameTestTags {
  const val SCROLLER = "scroller"
  const val GIVEN_NAME_INPUT = "given-name-input"
  const val FAMILY_NAME_INPUT = "family-name-input"
  const val BUTTON_SAVE = "button-save"
}

/**
 * Lets the user change the first and last name on their profile.
 */
@Composable
fun EditProfileNameScreen(
  state: EditProfileNameState,
  onEvent: (EditProfileNameEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocusWhenReady()
  }

  Scaffolds.Settings(
    title = stringResource(R.string.EditProfileNameFragment_your_name),
    onNavigationClick = { onEvent(EditProfileNameEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.X.imageVector,
    snackbarHost = {
      Snackbars.Host(
        message = state.snackbar.message(),
        onDismissed = { onEvent(EditProfileNameEvent.SnackbarDismissed) }
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
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .testTag(EditProfileNameTestTags.SCROLLER)
      ) {
        NameField(
          value = state.givenName,
          label = stringResource(R.string.EditProfileNameFragment_first_name),
          enabled = !state.saving,
          imeAction = ImeAction.Next,
          autofillHint = HintConstants.AUTOFILL_HINT_PERSON_NAME_GIVEN,
          onValueChange = { onEvent(EditProfileNameEvent.GivenNameChanged(it)) },
          onImeAction = null,
          focusRequester = focusRequester,
          modifier = Modifier.testTag(EditProfileNameTestTags.GIVEN_NAME_INPUT)
        )

        Spacer(modifier = Modifier.height(20.dp))

        NameField(
          value = state.familyName,
          label = stringResource(R.string.EditProfileNameFragment_last_name_optional),
          enabled = !state.saving,
          imeAction = ImeAction.Done,
          autofillHint = HintConstants.AUTOFILL_HINT_PERSON_NAME_FAMILY,
          onValueChange = { onEvent(EditProfileNameEvent.FamilyNameChanged(it)) },
          onImeAction = { if (state.canSave) onEvent(EditProfileNameEvent.SaveClicked) },
          modifier = Modifier.testTag(EditProfileNameTestTags.FAMILY_NAME_INPUT)
        )
      }

      ProgressTonalButton(
        text = stringResource(R.string.EditProfileNameScreen__save),
        onClick = { onEvent(EditProfileNameEvent.SaveClicked) },
        inProgress = state.saving,
        enabled = state.canSave,
        modifier = Modifier
          .padding(16.dp)
          .testTag(EditProfileNameTestTags.BUTTON_SAVE)
      )
    }
  }
}

@Composable
private fun NameField(
  value: String,
  label: String,
  enabled: Boolean,
  imeAction: ImeAction,
  autofillHint: String,
  onValueChange: (String) -> Unit,
  onImeAction: (() -> Unit)?,
  modifier: Modifier = Modifier,
  focusRequester: FocusRequester? = null
) {
  val interactionSource = remember { MutableInteractionSource() }

  EmojiTextField(
    value = value,
    onValueChange = onValueChange,
    label = { TextFields.Label(label, value.isNotEmpty(), interactionSource) },
    interactionSource = interactionSource,
    singleLine = true,
    enabled = enabled,
    capitalization = KeyboardCapitalization.Words,
    imeAction = imeAction,
    onImeAction = onImeAction,
    autofillHint = autofillHint,
    focusRequester = focusRequester,
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
  )
}

@Composable
private fun EditProfileNameState.Snackbar.message(): String? {
  return when (this) {
    EditProfileNameState.Snackbar.None -> null
    EditProfileNameState.Snackbar.NetworkFailure -> stringResource(R.string.EditProfileNameScreen__failed_to_save_due_to_network_issues)
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileNameScreenPreview() {
  EditProfileNameScreen(
    state = EditProfileNameState(givenName = "Alex", familyName = "Hart"),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileNameScreenEmptyPreview() {
  EditProfileNameScreen(
    state = EditProfileNameState(),
    onEvent = {}
  )
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun EditProfileNameScreenSavingPreview() {
  EditProfileNameScreen(
    state = EditProfileNameState(givenName = "Alex", familyName = "Hart", saving = true),
    onEvent = {}
  )
}
