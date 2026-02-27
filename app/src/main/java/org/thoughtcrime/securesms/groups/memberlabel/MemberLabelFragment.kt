/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.CircularProgressWrapper
import org.signal.core.ui.compose.ClearableTextField
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelUiState.SaveState
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.viewModel

/**
 * Screen for editing a user's group-specific label and emoji.
 */
class MemberLabelFragment : ComposeFragment(), ReactWithAnyEmojiBottomSheetDialogFragment.Callback {
  companion object {
    private const val EMOJI_PICKER_DIALOG_TAG = "emoji_picker_dialog"
    private const val ARG_GROUP_ID = "group_id"

    fun newInstance(groupId: GroupId.V2): MemberLabelFragment {
      return MemberLabelFragment().apply {
        arguments = bundleOf(ARG_GROUP_ID to groupId)
      }
    }
  }

  private val groupId: GroupId.V2 by lazy {
    requireArguments().requireParcelableCompat(ARG_GROUP_ID, GroupId.V2::class.java)
  }

  private val viewModel: MemberLabelViewModel by viewModel {
    MemberLabelViewModel(
      groupId = groupId,
      recipientId = Recipient.self().id
    )
  }

  @Composable
  override fun FragmentContent() {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }

    val callbacks = remember {
      object : MemberLabelUiCallbacks {
        override fun onClosePressed() {
          backPressedDispatcher?.onBackPressed()
        }

        override fun onLabelEmojiChanged(emoji: String) = viewModel.onLabelEmojiChanged(emoji)
        override fun onLabelTextChanged(text: String) = viewModel.onLabelTextChanged(text)
        override fun onSetEmojiClicked() = showEmojiPicker()
        override fun onClearLabelClicked() = viewModel.clearLabel()
        override fun onSaveClicked() = viewModel.save()
      }
    }

    val networkErrorMessage = stringResource(R.string.GroupMemberLabel__error_cant_save_no_network)
    val noPermissionErrorMessage = stringResource(R.string.GroupMemberLabel__error_no_edit_permission)

    LaunchedEffect(uiState.saveState) {
      when (uiState.saveState) {
        is SaveState.Success -> {
          backPressedDispatcher?.onBackPressed()
          viewModel.onSaveStateConsumed()
        }

        is SaveState.NetworkError -> {
          snackbarHostState.showSnackbar(networkErrorMessage)
          viewModel.onSaveStateConsumed()
        }

        is SaveState.InsufficientRights -> {
          snackbarHostState.showSnackbar(noPermissionErrorMessage)
          viewModel.onSaveStateConsumed()
        }

        is SaveState.InProgress, null -> Unit
      }
    }

    MemberLabelScreenUi(
      state = uiState,
      callbacks = callbacks,
      snackbarHostState = snackbarHostState
    )
  }

  private fun showEmojiPicker() {
    ReactWithAnyEmojiBottomSheetDialogFragment.createForAboutSelection()
      .show(childFragmentManager, EMOJI_PICKER_DIALOG_TAG)
  }

  override fun onReactWithAnyEmojiSelected(emoji: String) = viewModel.onLabelEmojiChanged(emoji)
  override fun onReactWithAnyEmojiDialogDismissed() = Unit
}

@Composable
private fun MemberLabelScreenUi(
  state: MemberLabelUiState,
  callbacks: MemberLabelUiCallbacks,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
  Scaffolds.Settings(
    title = stringResource(R.string.GroupMemberLabel__title),
    onNavigationClick = callbacks::onClosePressed,
    navigationIcon = SignalIcons.X.imageVector,
    navigationContentDescription = stringResource(R.string.GroupMemberLabel__accessibility_close_screen),
    snackbarHost = { SnackbarHost(snackbarHostState) }
  ) { paddingValues ->

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }

    Box(
      modifier = Modifier
        .padding(paddingValues)
        .consumeWindowInsets(paddingValues)
        .fillMaxSize()
        .imePadding()
    ) {
      Column(
        modifier = Modifier
          .padding(horizontal = 24.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = stringResource(R.string.GroupMemberLabel__description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        LabelTextField(
          labelEmoji = state.labelEmoji,
          labelText = state.labelText,
          remainingCharacters = state.remainingCharacters,
          onLabelTextChange = callbacks::onLabelTextChanged,
          onEmojiChange = callbacks::onSetEmojiClicked,
          onClear = callbacks::onClearLabelClicked,
          onSave = callbacks::onSaveClicked,
          modifier = Modifier.focusRequester(focusRequester)
        )

        Text(
          text = stringResource(R.string.GroupMemberLabel__preview_section_header),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
        )

        if (state.recipient != null) {
          MessageBubblePreview(
            sender = state.recipient,
            senderNameColor = state.senderNameColor,
            labelEmoji = state.labelEmoji,
            labelText = state.sanitizedLabelText,
            messageText = stringResource(R.string.GroupMemberLabel__preview_sample_message)
          )
        }
      }

      CircularProgressWrapper(
        isLoading = state.saveState is SaveState.InProgress,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 24.dp, bottom = 16.dp)
      ) {
        SaveButton(
          enabled = state.isSaveEnabled,
          onClick = callbacks::onSaveClicked
        )
      }
    }
  }
}

@Composable
private fun LabelTextField(
  labelEmoji: String?,
  labelText: String,
  remainingCharacters: Int,
  onLabelTextChange: (String) -> Unit,
  onEmojiChange: () -> Unit,
  onClear: () -> Unit,
  onSave: () -> Unit,
  modifier: Modifier = Modifier
) {
  ClearableTextField(
    value = labelText,
    onValueChange = onLabelTextChange,
    onClear = onClear,
    clearContentDescription = stringResource(R.string.GroupMemberLabel__accessibility_clear_label),
    placeholder = { Text(stringResource(R.string.GroupMemberLabel__label_text_placeholder)) },
    leadingIcon = {
      EmojiPickerButton(
        selectedEmoji = labelEmoji,
        onEmojiSelected = onEmojiChange
      )
    },
    enabled = true,
    singleLine = true,
    keyboardOptions = KeyboardOptions(
      capitalization = KeyboardCapitalization.Sentences,
      imeAction = ImeAction.Done
    ),
    keyboardActions = KeyboardActions(onDone = { onSave() }),
    hasClearableContent = { labelText.isNotEmpty() || !labelEmoji.isNullOrEmpty() },
    charactersRemainingBeforeLimit = remainingCharacters,
    countdownConfig = ClearableTextField.CountdownConfig(displayThreshold = 9, warnThreshold = 5),
    colors = TextFieldDefaults.colors(
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
    ),
    modifier = modifier
  )
}

@Composable
private fun EmojiPickerButton(
  onEmojiSelected: () -> Unit,
  selectedEmoji: String?
) {
  IconButton(
    onClick = onEmojiSelected
  ) {
    if (selectedEmoji.isNotNullOrBlank()) {
      Text(
        text = selectedEmoji,
        style = MaterialTheme.typography.bodyLarge
      )
    } else {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_emoji_plus_24),
        contentDescription = stringResource(R.string.GroupMemberLabel__accessibility_select_emoji),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

@Composable
private fun SaveButton(
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Buttons.LargeTonal(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
  ) {
    Text(text = stringResource(R.string.GroupMemberLabel__save))
  }
}

private interface MemberLabelUiCallbacks {
  fun onClosePressed()
  fun onLabelEmojiChanged(emoji: String)
  fun onLabelTextChanged(text: String)
  fun onSetEmojiClicked()
  fun onClearLabelClicked()
  fun onSaveClicked()

  object Empty : MemberLabelUiCallbacks {
    override fun onClosePressed() = Unit
    override fun onLabelEmojiChanged(emoji: String) = Unit
    override fun onLabelTextChanged(text: String) = Unit
    override fun onSetEmojiClicked() = Unit
    override fun onClearLabelClicked() = Unit
    override fun onSaveClicked() = Unit
  }
}

@AllDevicePreviews
@Composable
private fun MemberLabelScreenPreview() {
  Previews.Preview {
    MemberLabelScreenUi(
      state = MemberLabelUiState(
        recipient = Recipient(
          profileName = ProfileName.fromParts("Kahless", "The Unforgettable")
        ),
        labelEmoji = "⛑️",
        labelText = "Vet Coordinator"
      ),
      callbacks = MemberLabelUiCallbacks.Empty
    )
  }
}
