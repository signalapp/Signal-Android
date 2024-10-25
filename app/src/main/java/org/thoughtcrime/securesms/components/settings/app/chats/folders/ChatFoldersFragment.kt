package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.DropdownMenus
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.copied.androidx.compose.DraggableItem
import org.signal.core.ui.copied.androidx.compose.dragContainer
import org.signal.core.ui.copied.androidx.compose.rememberDragDropState
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment that displays current and suggested chat folders
 */
class ChatFoldersFragment : ComposeFragment() {

  private val viewModel: ChatFoldersViewModel by activityViewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    viewModel.loadCurrentFolders(requireContext())

    Scaffolds.Settings(
      title = stringResource(id = R.string.ChatsSettingsFragment__chat_folders),
      onNavigationClick = { navController.popBackStack() },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      FoldersScreen(
        state = state,
        navController = navController,
        modifier = Modifier.padding(contentPadding),
        onFolderClicked = {
          navController.safeNavigate(ChatFoldersFragmentDirections.actionChatFoldersFragmentToCreateFoldersFragment(it.id, -1))
        },
        onAdd = { folder ->
          Toast.makeText(requireContext(), getString(R.string.ChatFoldersFragment__folder_added, folder.name), Toast.LENGTH_SHORT).show()
          viewModel.createFolder(requireContext(), folder)
        },
        onDeleteClicked = { folder ->
          viewModel.setCurrentFolder(folder)
          viewModel.showDeleteDialog(true)
        },
        onDeleteConfirmed = {
          viewModel.deleteFolder(context = requireContext())
        },
        onDeleteDismissed = {
          viewModel.showDeleteDialog(false)
        },
        onPositionUpdated = { fromIndex, toIndex -> viewModel.updatePosition(fromIndex, toIndex) }
      )
    }
  }
}

@Composable
fun FoldersScreen(
  state: ChatFoldersSettingsState,
  navController: NavController? = null,
  modifier: Modifier = Modifier,
  onFolderClicked: (ChatFolderRecord) -> Unit = {},
  onAdd: (ChatFolderRecord) -> Unit = {},
  onDeleteClicked: (ChatFolderRecord) -> Unit = {},
  onDeleteConfirmed: () -> Unit = {},
  onDeleteDismissed: () -> Unit = {},
  onPositionUpdated: (Int, Int) -> Unit = { _, _ -> }
) {
  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val isRtl = ViewUtil.isRtl(LocalContext.current)
  val listState = rememberLazyListState()
  val dragDropState =
    rememberDragDropState(listState, includeHeader = true, includeFooter = true) { fromIndex, toIndex ->
      onPositionUpdated(fromIndex, toIndex)
    }

  LaunchedEffect(Unit) {
    if (!SignalStore.uiHints.hasSeenChatFoldersEducationSheet) {
      SignalStore.uiHints.hasSeenChatFoldersEducationSheet = true
      navController?.safeNavigate(R.id.action_chatFoldersFragment_to_chatFoldersEducationSheet)
    }
  }

  if (state.showDeleteDialog) {
    Dialogs.SimpleAlertDialog(
      title = "",
      body = stringResource(id = R.string.CreateFoldersFragment__delete_this_chat_folder),
      confirm = stringResource(id = R.string.delete),
      onConfirm = onDeleteConfirmed,
      dismiss = stringResource(id = android.R.string.cancel),
      onDismiss = onDeleteDismissed
    )
  }

  LazyColumn(
    modifier = Modifier.dragContainer(
      dragDropState = dragDropState,
      leftDpOffset = if (isRtl) 0.dp else screenWidth - 48.dp,
      rightDpOffset = if (isRtl) 48.dp else screenWidth
    ),
    state = listState
  ) {
    item {
      DraggableItem(dragDropState, 0) {
        Text(
          text = stringResource(id = R.string.ChatFoldersFragment__organize_your_chats),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = modifier.padding(top = 12.dp, bottom = 12.dp, end = 12.dp, start = 24.dp)
        )
        Text(
          text = stringResource(id = R.string.ChatFoldersFragment__folders),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 24.dp)
        )
        FolderRow(
          icon = R.drawable.symbol_plus_compact_16,
          title = stringResource(R.string.ChatFoldersFragment__create_a_folder),
          onClick = { onFolderClicked(ChatFolderRecord()) }
        )
      }
    }

    itemsIndexed(state.folders) { index, folder ->
      DraggableItem(dragDropState, 1 + index) { isDragging ->
        val elevation = if (isDragging) 1.dp else 0.dp
        val isAllChats = folder.folderType == ChatFolderRecord.FolderType.ALL
        FolderRow(
          icon = R.drawable.symbol_folder_24,
          title = if (isAllChats) stringResource(R.string.ChatFoldersFragment__all_chats) else folder.name,
          subtitle = getFolderDescription(folder),
          onClick = if (!isAllChats) {
            { onFolderClicked(folder) }
          } else null,
          onDelete = { onDeleteClicked(folder) },
          elevation = elevation,
          showDragHandle = true
        )
      }
    }

    item {
      DraggableItem(dragDropState, 1 + state.folders.size) {
        if (state.suggestedFolders.isNotEmpty()) {
          Dividers.Default()

          Text(
            text = stringResource(id = R.string.ChatFoldersFragment__suggested_folders),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 24.dp)
          )
        }

        state.suggestedFolders.forEach { chatFolder ->
          when (chatFolder.folderType) {
            ChatFolderRecord.FolderType.UNREAD -> {
              val title: String = stringResource(R.string.ChatFoldersFragment__unread)
              FolderRow(
                icon = R.drawable.symbol_chat_badge_24,
                title = title,
                subtitle = stringResource(R.string.ChatFoldersFragment__unread_messages),
                onAdd = { onAdd(chatFolder) }
              )
            }
            ChatFolderRecord.FolderType.INDIVIDUAL -> {
              val title: String = stringResource(R.string.ChatFoldersFragment__one_on_one_chats)
              FolderRow(
                icon = R.drawable.symbol_person_light_24,
                title = title,
                subtitle = stringResource(R.string.ChatFoldersFragment__only_direct_messages),
                onAdd = { onAdd(chatFolder) }
              )
            }
            ChatFolderRecord.FolderType.GROUP -> {
              val title: String = stringResource(R.string.ChatFoldersFragment__groups)
              FolderRow(
                icon = R.drawable.symbol_group_light_20,
                title = title,
                subtitle = stringResource(R.string.ChatFoldersFragment__only_group_messages),
                onAdd = { onAdd(chatFolder) }
              )
            }
            ChatFolderRecord.FolderType.ALL -> {
              error("All chats should not be suggested")
            }
            ChatFolderRecord.FolderType.CUSTOM -> {
              error("Custom folders should not be suggested")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun getFolderDescription(folder: ChatFolderRecord): String {
  val chatTypeCount = folder.showIndividualChats.toInt() + folder.showGroupChats.toInt()
  val chatTypes = pluralStringResource(id = R.plurals.ChatFoldersFragment__d_chat_types, count = chatTypeCount, chatTypeCount)
  val includedChats = pluralStringResource(id = R.plurals.ChatFoldersFragment__d_chats, count = folder.includedChats.size, folder.includedChats.size)
  val excludedChats = pluralStringResource(id = R.plurals.ChatFoldersFragment__d_chats_excluded, count = folder.excludedChats.size, folder.excludedChats.size)

  return remember(chatTypeCount, folder.includedChats.size, folder.excludedChats.size) {
    val description = mutableListOf<String>()
    if (chatTypeCount != 0) {
      description.add(chatTypes)
    }
    if (folder.includedChats.isNotEmpty()) {
      description.add(includedChats)
    }
    if (folder.excludedChats.isNotEmpty()) {
      description.add(excludedChats)
    }
    description.joinToString(separator = ", ")
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderRow(
  modifier: Modifier = Modifier,
  icon: Int,
  title: String,
  subtitle: String = "",
  onClick: (() -> Unit)? = null,
  onAdd: (() -> Unit)? = null,
  onDelete: (() -> Unit)? = null,
  elevation: Dp = 0.dp,
  showDragHandle: Boolean = false
) {
  val menuController = remember { DropdownMenus.MenuController() }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = if (onClick != null && onDelete != null) {
      modifier
        .combinedClickable(
          onClick = onClick,
          onLongClick = { menuController.show() }
        )
        .fillMaxWidth()
        .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        .shadow(elevation = elevation)
        .background(MaterialTheme.colorScheme.background)
        .padding(start = 24.dp, end = 12.dp)
    } else if (onClick != null) {
      modifier
        .clickable(onClick = onClick)
        .fillMaxWidth()
        .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        .shadow(elevation = elevation)
        .background(MaterialTheme.colorScheme.background)
        .padding(start = 24.dp, end = 12.dp)
    } else {
      modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        .shadow(elevation = elevation)
        .background(MaterialTheme.colorScheme.background)
        .padding(start = 24.dp, end = 12.dp)
    }
  ) {
    Image(
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
      imageVector = ImageVector.vectorResource(id = icon),
      contentDescription = null,
      modifier = modifier
        .size(40.dp)
        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
        .padding(8.dp)
    )
    Column(
      modifier = Modifier
        .padding(start = 12.dp)
        .weight(1f)
    ) {
      Text(text = title)
      if (subtitle.isNotEmpty()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (onAdd != null) {
      Buttons.Small(onClick = onAdd, modifier = modifier.padding(end = 12.dp)) {
        Text(stringResource(id = R.string.ChatFoldersFragment__add))
      }
    } else if (showDragHandle) {
      Icon(
        painter = painterResource(id = R.drawable.ic_drag_handle),
        contentDescription = null,
        modifier = modifier.padding(end = 12.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    DropdownMenus.Menu(controller = menuController, offsetX = 0.dp, offsetY = 4.dp) { menuController ->
      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(onClick = {
              onClick!!()
              menuController.hide()
            })
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.symbol_edit_24),
            contentDescription = null
          )
          Text(
            text = stringResource(R.string.ChatFoldersFragment__edit_folder),
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(onClick = {
              onDelete!!()
              menuController.hide()
            })
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.symbol_trash_24),
            contentDescription = null
          )
          Text(
            text = stringResource(R.string.CreateFoldersFragment__delete_folder),
            modifier = Modifier.padding(horizontal = 16.dp)
          )
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun ChatFolderPreview() {
  val previewFolders = listOf(
    ChatFolderRecord(
      id = 1,
      name = "Work",
      position = 1,
      showUnread = true,
      showIndividualChats = true,
      showGroupChats = true,
      showMutedChats = true,
      isMuted = false,
      folderType = ChatFolderRecord.FolderType.CUSTOM
    ),
    ChatFolderRecord(
      id = 2,
      name = "Fun People",
      position = 2,
      showUnread = true,
      showIndividualChats = true,
      showGroupChats = false,
      showMutedChats = false,
      isMuted = false,
      folderType = ChatFolderRecord.FolderType.CUSTOM
    )
  )

  Previews.Preview {
    FoldersScreen(
      ChatFoldersSettingsState(
        folders = previewFolders
      )
    )
  }
}
