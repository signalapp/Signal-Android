package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.routes.navigateTo
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.core.ui.R as CoreUiR

private const val MAX_CHAT_COUNT = 5

/**
 * Fragment that allows user to create, edit, or delete an individual folder
 */
class CreateFoldersFragment : ComposeFragment() {

  // Leaves by navigating up, since going back is intercepted to confirm discarding changes.
  private val appSettingsRouter by appSettingsRouter(
    onNavigateBack = { requireActivity().onNavigateUp() },
    onNavigateToScreen = ::navigateToScreen
  )

  @Composable
  override fun FragmentContent() {
    CreateFoldersEntry(
      folderId = arguments?.getLong(KEY_FOLDER_ID) ?: -1,
      threadIds = arguments?.getLongArray(KEY_THREAD_IDS),
      parentEventEmitter = appSettingsRouter::onEvent
    )
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      is AppSettingsRoute.ChatFoldersRoute.ChooseChats -> findNavController().safeNavigate(
        CreateFoldersFragmentDirections.actionCreateFoldersFragmentToChooseChatsFragment(route.includeChats)
      )
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  companion object {
    private val KEY_FOLDER_ID = "folder_id"
    private val KEY_THREAD_IDS = "thread_ids"
  }
}

/**
 * Creates, edits or deletes the folder [folderId] (-1 for a new one, starting with [threadIds]), wired to the view
 * model the folder screens share, leaving through [parentEventEmitter]. Going back with unsaved changes asks first.
 */
@Composable
fun CreateFoldersEntry(
  folderId: Long,
  threadIds: LongArray?,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val context = LocalContext.current
  val activity = requireNotNull(LocalActivity.current) as FragmentActivity
  val viewModel = chatFoldersViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val focusRequester = remember { FocusRequester() }
  val isNewFolder = state.originalFolder.folderRecord.id == -1L
  val hasUnsavedChanges = viewModel.hasChanges() && !viewModel.hasEmptyName()

  BackHandler(enabled = hasUnsavedChanges) {
    viewModel.showConfirmationDialog(true)
  }

  LaunchedEffect(Unit) {
    if (viewModel.shouldSetInitialFolder()) {
      viewModel.setCurrentFolderId(folderId)
      viewModel.addThreadsToFolder(threadIds)
    }
  }

  LaunchedEffect(Unit) {
    if (viewModel.shouldShowEducationSheet()) {
      SignalStore.uiHints.hasSeenChatFoldersEducationSheet = true
      ChatFoldersEducationSheet().show(activity.supportFragmentManager, null)
    }
  }

  Scaffolds.Settings(
    title = if (isNewFolder) stringResource(id = R.string.CreateFoldersFragment__create_a_folder) else stringResource(id = R.string.CreateFoldersFragment__edit_folder),
    onNavigationClick = {
      if (hasUnsavedChanges) {
        viewModel.showConfirmationDialog(true)
      } else {
        parentEventEmitter.navigateBack()
      }
    },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description)
  ) { contentPadding: PaddingValues ->
    CreateFolderScreen(
      state = state,
      focusRequester = focusRequester,
      modifier = Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .imePadding(),
      isNewFolder = isNewFolder,
      hasChanges = viewModel.hasChanges(),
      onAddChat = {
        viewModel.setPendingChats()
        parentEventEmitter.navigateTo(AppSettingsRoute.ChatFoldersRoute.ChooseChats(includeChats = true))
      },
      onRemoveChat = {
        viewModel.setPendingChats()
        parentEventEmitter.navigateTo(AppSettingsRoute.ChatFoldersRoute.ChooseChats(includeChats = false))
      },
      onNameChange = { viewModel.updateName(it) },
      onToggleShowUnread = { viewModel.toggleShowUnread(it) },
      onToggleShowMuted = { viewModel.toggleShowMutedChats(it) },
      onDeleteClicked = { viewModel.showDeleteDialog(true) },
      onDeleteConfirmed = {
        viewModel.deleteFolder(context)
        parentEventEmitter.navigateBack()
      },
      onDeleteDismissed = {
        viewModel.showDeleteDialog(false)
      },
      onCreateConfirmed = {
        if (isNewFolder) {
          viewModel.createFolder(context)
        } else {
          viewModel.updateFolder(context)
        }
        parentEventEmitter.navigateBack()
      },
      onCreateDismissed = { shouldExit ->
        viewModel.showConfirmationDialog(false)
        if (shouldExit) {
          parentEventEmitter.navigateBack()
        }
      },
      onShowToast = {
        Toast.makeText(context, R.string.CreateFoldersFragment__please_enter_name, Toast.LENGTH_LONG).show()
      }
    )
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
  onCreateConfirmed: () -> Unit = {},
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
          value = state.currentFolder.folderRecord.name,
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

        if (state.currentFolder.folderRecord.showIndividualChats) {
          FolderRow(
            icon = R.drawable.symbol_person_light_24,
            title = stringResource(R.string.ChatFoldersFragment__one_on_one_chats),
            onClick = onAddChat
          )
        }

        if (state.currentFolder.folderRecord.showGroupChats) {
          FolderRow(
            icon = R.drawable.symbol_group_light_20,
            title = stringResource(R.string.ChatFoldersFragment__groups),
            onClick = onAddChat
          )
        }
      }

      if (!expandIncluded && state.currentFolder.includedRecipients.size > MAX_CHAT_COUNT) {
        items(state.currentFolder.includedRecipients.take(MAX_CHAT_COUNT)) { recipient ->
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

      item { Spacer(modifier = Modifier.height(60.dp)) }
    }

    Buttons.MediumTonal(
      colors = ButtonDefaults.filledTonalButtonColors(
        contentColor = if (state.currentFolder.folderRecord.name.isBlank()) {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        } else {
          MaterialTheme.colorScheme.onSurface
        },
        containerColor = if (state.currentFolder.folderRecord.name.isBlank()) {
          MaterialTheme.colorScheme.surfaceVariant
        } else {
          MaterialTheme.colorScheme.primaryContainer
        },
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      enabled = hasChanges,
      onClick = {
        if (state.currentFolder.folderRecord.name.isBlank()) {
          onShowToast()
        } else {
          onCreateConfirmed()
        }
      },
      modifier = modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 16.dp)
    ) {
      if (isNewFolder) {
        Text(text = stringResource(R.string.CreateFoldersFragment__create))
      } else {
        Text(text = stringResource(R.string.CreateFoldersFragment__save))
      }
    }
  }
}

@Composable
private fun ShowUnreadSection(state: ChatFoldersSettingsState, onToggleShowUnread: (Boolean) -> Unit) {
  Row(
    modifier = Modifier
      .clickable { onToggleShowUnread(!state.currentFolder.folderRecord.showUnread) }
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
      checked = state.currentFolder.folderRecord.showUnread,
      onCheckedChange = onToggleShowUnread
    )
  }
}

@Composable
private fun ShowMutedSection(state: ChatFoldersSettingsState, onToggleShowMuted: (Boolean) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clickable { onToggleShowMuted(!state.currentFolder.folderRecord.showMutedChats) }
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
      checked = state.currentFolder.folderRecord.showMutedChats,
      onCheckedChange = onToggleShowMuted
    )
  }
}

@DayNightPreviews
@Composable
private fun CreateFolderPreview() {
  val previewFolder = ChatFolder(ChatFolderRecord(id = 1, name = "WIP"))

  Previews.Preview {
    CreateFolderScreen(
      state = ChatFoldersSettingsState(currentFolder = previewFolder),
      focusRequester = remember { FocusRequester() },
      isNewFolder = true
    )
  }
}

@DayNightPreviews
@Composable
private fun EditFolderPreview() {
  val previewFolder = ChatFolder(ChatFolderRecord(id = 1, name = "Work"))

  Previews.Preview {
    CreateFolderScreen(
      state = ChatFoldersSettingsState(originalFolder = previewFolder),
      focusRequester = remember { FocusRequester() },
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
        imageVector = ImageVector.vectorResource(R.drawable.symbol_person_fill_24),
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
