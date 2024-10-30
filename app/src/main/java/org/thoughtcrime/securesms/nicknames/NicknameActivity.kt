/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.nicknames

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.TextFields
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.viewModel
import org.signal.core.ui.R as CoreUiR

/**
 * Fragment allowing a user to set a custom nickname for the given recipient.
 */
class NicknameActivity : PassphraseRequiredActivity(), NicknameContentCallback {

  private val theme = DynamicNoActionBarTheme()

  private val args: Args by lazy {
    Args.fromBundle(intent.extras!!)
  }

  private val viewModel: NicknameViewModel by viewModel {
    NicknameViewModel(args.recipientId)
  }

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    setContent {
      val state by viewModel.state

      LaunchedEffect(state.formState) {
        if (state.formState == NicknameState.FormState.SAVED) {
          supportFinishAfterTransition()
        }
      }

      SignalTheme {
        NicknameContent(
          callback = remember { this },
          state = state,
          focusNoteFirst = args.focusNoteFirst
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  override fun onNavigationClick() {
    supportFinishAfterTransition()
  }

  override fun onSaveClick() {
    viewModel.save()
  }

  override fun onDeleteClick() {
    viewModel.delete()
  }

  override fun onFirstNameChanged(value: String) {
    viewModel.onFirstNameChanged(value)
  }

  override fun onLastNameChanged(value: String) {
    viewModel.onLastNameChanged(value)
  }

  override fun onNoteChanged(value: String) {
    viewModel.onNoteChanged(value)
  }

  /**
   * @param recipientId     The recipient to edit the nickname and note for
   * @param focusNoteFirst  Whether default focus should be on the edit note field
   */
  data class Args(
    val recipientId: RecipientId,
    val focusNoteFirst: Boolean
  ) {
    fun toBundle(): Bundle {
      return bundleOf(
        RECIPIENT_ID to recipientId,
        FOCUS_NOTE_FIRST to focusNoteFirst
      )
    }

    companion object {
      private const val RECIPIENT_ID = "recipient_id"
      private const val FOCUS_NOTE_FIRST = "focus_note_first"

      fun fromBundle(bundle: Bundle): Args {
        return Args(
          recipientId = bundle.getParcelableCompat(RECIPIENT_ID, RecipientId::class.java)!!,
          focusNoteFirst = bundle.getBoolean(FOCUS_NOTE_FIRST)
        )
      }
    }
  }

  /**
   * Launches the nickname activity with the proper arguments.
   * Doesn't return a response, but is a helpful signal to know when to refresh UI.
   */
  class Contract : ActivityResultContract<Args, Unit>() {
    override fun createIntent(context: Context, input: Args): Intent {
      return Intent(context, NicknameActivity::class.java).putExtras(input.toBundle())
    }

    override fun parseResult(resultCode: Int, intent: Intent?) = Unit
  }
}

private interface NicknameContentCallback {
  fun onNavigationClick()
  fun onSaveClick()
  fun onDeleteClick()
  fun onFirstNameChanged(value: String)
  fun onLastNameChanged(value: String)
  fun onNoteChanged(value: String)
}

@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NicknameContentPreview() {
  Previews.Preview {
    val callback = remember {
      object : NicknameContentCallback {
        override fun onNavigationClick() = Unit
        override fun onSaveClick() = Unit
        override fun onDeleteClick() = Unit
        override fun onFirstNameChanged(value: String) = Unit
        override fun onLastNameChanged(value: String) = Unit
        override fun onNoteChanged(value: String) = Unit
      }
    }

    NicknameContent(
      callback = callback,
      state = NicknameState(
        isEditing = true,
        note = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod temor incididunt ut labore et dolore magna aliqua. Ut enim ad minimu"
      ),
      focusNoteFirst = false
    )
  }
}

@Composable
private fun NicknameContent(
  callback: NicknameContentCallback,
  state: NicknameState,
  focusNoteFirst: Boolean
) {
  var displayDeletionDialog by remember { mutableStateOf(false) }

  Scaffolds.Settings(
    title = stringResource(id = R.string.NicknameActivity__nickname),
    onNavigationClick = callback::onNavigationClick,
    navigationIconPainter = painterResource(id = R.drawable.symbol_arrow_left_24)
  ) { paddingValues ->

    val firstNameFocusRequester = remember { FocusRequester() }
    val noteFocusRequester = remember { FocusRequester() }

    Column(
      modifier = Modifier
        .padding(paddingValues)
        .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
    ) {
      LazyColumn(modifier = Modifier.weight(1f)) {
        item {
          Text(
            text = stringResource(id = R.string.NicknameActivity__nicknames_amp_notes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
          )
        }

        item {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 24.dp)
          ) {
            if (state.recipient != null) {
              AvatarImage(recipient = state.recipient, modifier = Modifier.size(80.dp))
            } else {
              Spacer(modifier = Modifier.size(80.dp))
            }
          }
        }

        item {
          ClearableTextField(
            value = state.firstName,
            hint = stringResource(id = R.string.NicknameActivity__first_name),
            clearContentDescription = stringResource(id = R.string.NicknameActivity__clear_first_name),
            enabled = true,
            singleLine = true,
            onValueChange = callback::onFirstNameChanged,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
              .focusRequester(firstNameFocusRequester)
              .fillMaxWidth()
              .padding(bottom = 20.dp)
          )
        }

        item {
          ClearableTextField(
            value = state.lastName,
            hint = stringResource(id = R.string.NicknameActivity__last_name),
            clearContentDescription = stringResource(id = R.string.NicknameActivity__clear_last_name),
            enabled = true,
            singleLine = true,
            onValueChange = callback::onLastNameChanged,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 20.dp)
          )
        }

        item {
          ClearableTextField(
            value = state.note,
            hint = stringResource(id = R.string.NicknameActivity__note),
            clearContentDescription = "",
            clearable = false,
            enabled = true,
            onValueChange = callback::onNoteChanged,
            keyboardActions = KeyboardActions.Default,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            charactersRemaining = state.noteCharactersRemaining,
            modifier = Modifier
              .focusRequester(noteFocusRequester)
              .fillMaxWidth()
              .padding(bottom = 20.dp)
          )
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 20.dp)
      ) {
        if (state.isEditing) {
          TextButton(
            onClick = {
              displayDeletionDialog = true
            },
            modifier = Modifier
              .align(Alignment.BottomStart)
          ) {
            Text(
              text = stringResource(id = R.string.delete),
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
        Buttons.LargeTonal(
          onClick = callback::onSaveClick,
          enabled = state.canSave,
          modifier = Modifier
            .align(Alignment.BottomEnd)
        ) {
          Text(
            text = stringResource(id = R.string.NicknameActivity__save)
          )
        }
      }
    }

    if (displayDeletionDialog) {
      Dialogs.SimpleAlertDialog(
        title = stringResource(id = R.string.NicknameActivity__delete_nickname),
        body = stringResource(id = R.string.NicknameActivity__this_will_permanently_delete_this_nickname_and_note),
        confirm = stringResource(id = R.string.delete),
        dismiss = stringResource(id = android.R.string.cancel),
        onConfirm = {
          callback.onDeleteClick()
        },
        onDismiss = { displayDeletionDialog = false }
      )
    }

    LaunchedEffect(state.hasBecomeReady) {
      if (state.hasBecomeReady) {
        if (focusNoteFirst) {
          noteFocusRequester.requestFocus()
        } else {
          firstNameFocusRequester.requestFocus()
        }
      }
    }
  }
}

@Preview(name = "Light Theme", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ClearableTextFieldPreview() {
  Previews.Preview {
    val focusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.padding(16.dp)) {
      ClearableTextField(
        value = "",
        hint = "Without content",
        enabled = true,
        onValueChange = {},
        clearContentDescription = ""
      )
      Spacer(modifier = Modifier.size(16.dp))
      ClearableTextField(
        value = "Test",
        hint = "With Content",
        enabled = true,
        onValueChange = {},
        clearContentDescription = ""
      )
      Spacer(modifier = Modifier.size(16.dp))
      ClearableTextField(
        value = "",
        hint = "Disabled",
        enabled = false,
        onValueChange = {},
        clearContentDescription = ""
      )
      Spacer(modifier = Modifier.size(16.dp))
      ClearableTextField(
        value = "",
        hint = "Focused",
        enabled = true,
        onValueChange = {},
        modifier = Modifier.focusRequester(focusRequester),
        clearContentDescription = ""
      )
    }

    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
    }
  }
}

@Composable
private fun ClearableTextField(
  value: String,
  hint: String,
  clearContentDescription: String,
  enabled: Boolean,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  singleLine: Boolean = false,
  clearable: Boolean = true,
  charactersRemaining: Int = Int.MAX_VALUE,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
  var focused by remember { mutableStateOf(false) }

  val displayCountdown = charactersRemaining <= 100

  val clearButton: @Composable () -> Unit = {
    ClearButton(
      visible = focused,
      onClick = { onValueChange("") },
      contentDescription = clearContentDescription
    )
  }

  Box(modifier = modifier) {
    TextFields.TextField(
      value = value,
      onValueChange = onValueChange,
      label = {
        Text(text = hint)
      },
      enabled = enabled,
      singleLine = singleLine,
      keyboardActions = keyboardActions,
      keyboardOptions = keyboardOptions,
      modifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { focused = it.hasFocus && clearable },
      colors = TextFieldDefaults.colors(
        unfocusedLabelColor = MaterialTheme.colorScheme.outline,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
      ),
      trailingIcon = if (clearable) clearButton else null,
      contentPadding = TextFieldDefaults.contentPaddingWithLabel(end = if (displayCountdown) 48.dp else 16.dp)
    )

    AnimatedVisibility(
      visible = displayCountdown,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(bottom = 10.dp, end = 12.dp)
    ) {
      Text(
        text = "$charactersRemaining",
        style = MaterialTheme.typography.bodySmall,
        color = if (charactersRemaining <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
      )
    }
  }
}

@Composable
private fun ClearButton(
  visible: Boolean,
  onClick: () -> Unit,
  contentDescription: String
) {
  AnimatedVisibility(visible = visible) {
    IconButton(
      onClick = onClick
    ) {
      Icon(
        painter = painterResource(id = R.drawable.symbol_x_circle_fill_24),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.outline
      )
    }
  }
}
