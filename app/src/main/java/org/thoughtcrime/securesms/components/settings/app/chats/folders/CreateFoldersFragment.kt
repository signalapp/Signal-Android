package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val MAX_CHAT_COUNT = 5

/**
 * Fragment that allows user to create, edit, or delete an individual folder
 */
class CreateFoldersFragment : ComposeFragment() {

  private val viewModel: ChatFoldersViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (viewModel.hasChanges() && !viewModel.hasEmptyName()) {
            viewModel.showConfirmationDialog(true)
          } else {
            findNavController().popBackStack()
          }
        }
      }
    )
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val focusRequester = remember { FocusRequester() }
    val isNewFolder = state.originalFolder.id == -1L

    LaunchedEffect(Unit) {
      if (state.originalFolder == state.currentFolder) {
        viewModel.setCurrentFolderId(arguments?.getLong(KEY_FOLDER_ID) ?: -1)
        viewModel.addThreadToIncludedChat(arguments?.getLong(KEY_THREAD_ID))
      }
    }

    LaunchedEffect(Unit) {
      if (!SignalStore.uiHints.hasSeenChatFoldersEducationSheet) {
        SignalStore.uiHints.hasSeenChatFoldersEducationSheet = true
        navController.safeNavigate(R.id.action_createFoldersFragment_to_chatFoldersEducationSheet)
      }
    }

    Scaffolds.Settings(
      title = if (isNewFolder) stringResource(id = R.string.CreateFoldersFragment__create_a_folder) else stringResource(id = R.string.CreateFoldersFragment__edit_folder),
      onNavigationClick = {
        if (viewModel.hasChanges() && !viewModel.hasEmptyName()) {
          viewModel.showConfirmationDialog(true)
        } else {
          navController.popBackStack()
        }
      },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      CreateFolderScreen(
        state = state,
        focusRequester = focusRequester,
        modifier = Modifier.padding(contentPadding),
        isNewFolder = isNewFolder,
        hasChanges = viewModel.hasChanges(),
        onAddChat = {
          viewModel.setPendingChats()
          navController.safeNavigate(CreateFoldersFragmentDirections.actionCreateFoldersFragmentToChooseChatsFragment(true))
        },
        onRemoveChat = {
          viewModel.setPendingChats()
          navController.safeNavigate(CreateFoldersFragmentDirections.actionCreateFoldersFragmentToChooseChatsFragment(false))
        },
        onNameChange = { viewModel.updateName(it) },
        onToggleShowUnread = { viewModel.toggleShowUnread(it) },
        onToggleShowMuted = { viewModel.toggleShowMutedChats(it) },
        onDeleteClicked = { viewModel.showDeleteDialog(true) },
        onDeleteConfirmed = {
          viewModel.deleteFolder(requireContext())
          navController.popBackStack()
        },
        onDeleteDismissed = {
          viewModel.showDeleteDialog(false)
        },
        onCreateConfirmed = { shouldExit ->
          if (isNewFolder) {
            viewModel.createFolder(requireContext())
          } else {
            viewModel.updateFolder(requireContext())
          }
          if (shouldExit) {
            navController.popBackStack()
          }
        },
        onCreateDismissed = { shouldExit ->
          viewModel.showConfirmationDialog(false)
          if (shouldExit) {
            navController.popBackStack()
          }
        },
        onShowToast = {
          Toast.makeText(requireContext(), R.string.CreateFoldersFragment__please_enter_name, Toast.LENGTH_LONG).show()
        }
      )
    }
  }

  companion object {
    private val KEY_FOLDER_ID = "folder_id"
    private val KEY_THREAD_ID = "thread_id"
  }
}

@Composable
fun CreateFolderScreen(
  state: ChatFoldersSettingsState,
  focusRequester: FocusRequester,
  modifier: Modifier = Modifier,
  isNewFolder: Boolean = true,
  hasChanges: Boolean = false,
  onAddChat: () -> Unit = {},
  onRemoveChat: () -> Unit = {},
  onNameChange: (String) -> Unit = {},
  onToggleShowUnread: (Boolean) -> Unit = {},
  onToggleShowMuted: (Boolean) -> Unit = {},
  onDeleteClicked: () -> Unit = {},
  onDeleteConfirmed: () -> Unit = {},
  onDeleteDismissed: () -> Unit = {},
  onCreateConfirmed: (Boolean) -> Unit = {},
  onCreateDismissed: (Boolean) -> Unit = {},
  onShowToast: () -> Unit = {}
) {
  var expandIncluded by remember { mutableStateOf(false) }
  var expandExcluded by remember { mutableStateOf(false) }

  if (state.showDeleteDialog) {
    Dialogs.SimpleAlertDialog(
      title = "",
      body = stringResource(id = R.string.CreateFoldersFragment__delete_this_chat_folder),
      confirm = stringResource(id = R.string.delete),
      onConfirm = onDeleteConfirmed,
      dismiss = stringResource(id = android.R.string.cancel),
      onDismiss = onDeleteDismissed
    )
  } else if (state.showConfirmationDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.CreateFoldersFragment__discard_changes_title),
      body = stringResource(id = R.string.CreateFoldersFragment__you_will_lose_changes),
      confirm = stringResource(id = R.string.CreateFoldersFragment__discard),
      onConfirm = { onCreateDismissed(true) },
      dismiss = stringResource(id = android.R.string.cancel),
      onDismiss = { onCreateDismissed(false) },
      onDismissRequest = { onCreateDismissed(false) }
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn {
      item {
        TextField(
          value = state.currentFolder.name,
          label = { Text(text = stringResource(id = R.string.CreateFoldersFragment__folder_name)) },
          onValueChange = onNameChange,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          singleLine = true,
          modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .padding(top = 16.dp, bottom = 12.dp, start = 20.dp, end = 28.dp)
        )
      }

      item {
        Text(
          text = stringResource(id = R.string.CreateFoldersFragment__included_chats),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 24.dp)
        )
        FolderRow(
          icon = R.drawable.symbol_plus_compact_16,
          title = stringResource(R.string.CreateFoldersFragment__add_chats),
          onClick = onAddChat
        )

        if (state.currentFolder.showIndividualChats) {
          FolderRow(
            icon = R.drawable.symbol_person_light_24,
            title = stringResource(R.string.ChatFoldersFragment__one_on_one_chats),
            onClick = onAddChat
          )
        }

        if (state.currentFolder.showGroupChats) {
          FolderRow(
            icon = R.drawable.symbol_group_light_20,
            title = stringResource(R.string.ChatFoldersFragment__groups),
            onClick = onAddChat
          )
        }
      }

      if (!expandIncluded && state.currentFolder.includedRecipients.size > MAX_CHAT_COUNT) {
        items(state.currentFolder.includedRecipients.toList().subList(0, MAX_CHAT_COUNT)) { recipient ->
          ChatRow(
            recipient = recipient,
            onClick = onAddChat
          )
        }
        item {
          FolderRow(
            icon = R.drawable.symbol_chevron_down_24,
            title = stringResource(R.string.CreateFoldersFragment__see_all),
            onClick = { expandIncluded = true }
          )
        }
      } else {
        items(state.currentFolder.includedRecipients.toList()) { recipient ->
          ChatRow(
            recipient = recipient,
            onClick = onAddChat
          )
        }
      }

      item {
        Text(
          text = stringResource(id = R.string.CreateFoldersFragment__choose_chats_you_want),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, start = 24.dp)
        )
      }

      item {
        Text(
          text = stringResource(id = R.string.CreateFoldersFragment__exceptions),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 24.dp, bottom = 12.dp, end = 12.dp, start = 24.dp)
        )
        FolderRow(
          icon = R.drawable.symbol_plus_compact_16,
          title = stringResource(R.string.CreateFoldersFragment__exclude_chats),
          onClick = onRemoveChat
        )
      }

      if (!expandExcluded && state.currentFolder.excludedRecipients.size > MAX_CHAT_COUNT) {
        items(state.currentFolder.excludedRecipients.toList().subList(0, MAX_CHAT_COUNT)) { recipient ->
          ChatRow(
            recipient = recipient,
            onClick = onAddChat
          )
        }
        item {
          FolderRow(
            icon = R.drawable.symbol_chevron_down_24,
            title = stringResource(R.string.CreateFoldersFragment__see_all),
            onClick = { expandExcluded = true }
          )
        }
      } else {
        items(state.currentFolder.excludedRecipients.toList()) { recipient ->
          ChatRow(
            recipient = recipient,
            onClick = onRemoveChat
          )
        }
      }

      item {
        Text(
          text = stringResource(id = R.string.CreateFoldersFragment__choose_chats_you_do_not_want),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, start = 24.dp, end = 12.dp)
        )
      }

      item {
        Dividers.Default()
        ShowUnreadSection(state, onToggleShowUnread)
        ShowMutedSection(state, onToggleShowMuted)

        if (!isNewFolder) {
          Dividers.Default()

          Text(
            text = stringResource(id = R.string.CreateFoldersFragment__delete_folder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
              .clickable { onDeleteClicked() }
              .fillMaxWidth()
              .padding(start = 24.dp, top = 16.dp, bottom = 32.dp)
          )
        }
      }

      if (hasChanges) {
        item { Spacer(modifier = Modifier.height(60.dp)) }
      }
    }

    if (hasChanges && isNewFolder) {
      Buttons.MediumTonal(
        onClick = { onCreateConfirmed(true) },
        modifier = modifier
          .align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = 16.dp)
      ) {
        Text(text = stringResource(R.string.CreateFoldersFragment__create))
      }
    } else if (!isNewFolder) {
      Buttons.MediumTonal(
        colors = ButtonDefaults.filledTonalButtonColors(
          contentColor = if (state.currentFolder.name.isEmpty()) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          } else {
            MaterialTheme.colorScheme.onSurface
          },
          containerColor = if (state.currentFolder.name.isEmpty()) {
            MaterialTheme.colorScheme.surfaceVariant
          } else {
            MaterialTheme.colorScheme.primaryContainer
          },
          disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        enabled = hasChanges,
        onClick = {
          if (state.currentFolder.name.isEmpty()) {
            onShowToast()
          } else {
            onCreateConfirmed(true)
          }
        },
        modifier = modifier
          .align(Alignment.BottomEnd)
          .padding(end = 16.dp, bottom = 16.dp)
      ) {
        Text(text = stringResource(R.string.CreateFoldersFragment__save))
      }
    }
  }
}

@Composable
private fun ShowUnreadSection(state: ChatFoldersSettingsState, onToggleShowUnread: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .padding(horizontal = 24.dp)
      .defaultMinSize(minHeight = 92.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(id = R.string.CreateFoldersFragment__only_show_unread_chats),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = stringResource(id = R.string.CreateFoldersFragment__when_enabled_only_chats),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Switch(
      checked = state.currentFolder.showUnread,
      onCheckedChange = onToggleShowUnread
    )
  }
}

@Composable
private fun ShowMutedSection(state: ChatFoldersSettingsState, onToggleShowMuted: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(horizontal = 24.dp)
      .defaultMinSize(minHeight = 56.dp)
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(id = R.string.CreateFoldersFragment__include_muted_chats),
        style = MaterialTheme.typography.bodyLarge
      )
    }
    Switch(
      checked = state.currentFolder.showMutedChats,
      onCheckedChange = onToggleShowMuted
    )
  }
}

@SignalPreview
@Composable
private fun CreateFolderPreview() {
  val previewFolder = ChatFolderRecord(id = 1, name = "WIP")

  Previews.Preview {
    CreateFolderScreen(
      state = ChatFoldersSettingsState(currentFolder = previewFolder),
      focusRequester = FocusRequester(),
      isNewFolder = true
    )
  }
}

@SignalPreview
@Composable
private fun EditFolderPreview() {
  val previewFolder = ChatFolderRecord(id = 1, name = "Work")

  Previews.Preview {
    CreateFolderScreen(
      state = ChatFoldersSettingsState(originalFolder = previewFolder),
      focusRequester = FocusRequester(),
      isNewFolder = false
    )
  }
}

@Composable
fun ChatRow(
  recipient: Recipient,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .clickable(onClick = onClick)
      .fillMaxWidth()
      .defaultMinSize(minHeight = 64.dp)
  ) {
    if (LocalInspectionMode.current) {
      Icon(
        imageVector = Icons.Default.Person,
        contentDescription = null,
        modifier = Modifier
          .padding(start = 24.dp, end = 16.dp)
          .size(40.dp)
          .background(
            color = Color.Red,
            shape = CircleShape
          )
      )
    } else {
      AvatarImage(
        recipient = recipient,
        modifier = Modifier
          .padding(start = 24.dp, end = 16.dp)
          .size(40.dp),
        useProfile = false
      )
    }

    Text(text = if (recipient.isSelf) stringResource(id = R.string.note_to_self) else recipient.getShortDisplayName(LocalContext.current))
  }
}
