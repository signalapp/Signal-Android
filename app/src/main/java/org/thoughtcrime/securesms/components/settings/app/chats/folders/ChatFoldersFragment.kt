package org.thoughtcrime.securesms.components.settings.app.chats.folders

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
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
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.copied.androidx.compose.DraggableItem
import org.signal.core.ui.copied.androidx.compose.dragContainer
import org.signal.core.ui.copied.androidx.compose.rememberDragDropState
import org.signal.core.util.toInt
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
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
        modifier = Modifier.padding(contentPadding),
        onFolderClicked = {
          navController.safeNavigate(ChatFoldersFragmentDirections.actionChatFoldersFragmentToCreateFoldersFragment(it.id))
        },
        onAdd = { folder ->
          Toast.makeText(requireContext(), getString(R.string.ChatFoldersFragment__folder_added, folder.name), Toast.LENGTH_SHORT).show()
          viewModel.createFolder(requireContext(), folder)
        },
        onPositionUpdated = { fromIndex, toIndex -> viewModel.updatePosition(fromIndex, toIndex) }
      )
    }
  }
}

@Composable
fun FoldersScreen(
  state: ChatFoldersSettingsState,
  modifier: Modifier = Modifier,
  onFolderClicked: (ChatFolderRecord) -> Unit = {},
  onAdd: (ChatFolderRecord) -> Unit = {},
  onPositionUpdated: (Int, Int) -> Unit = { _, _ -> }
) {
  val listState = rememberLazyListState()
  val dragDropState =
    rememberDragDropState(listState) { fromIndex, toIndex ->
      onPositionUpdated(fromIndex, toIndex)
    }

  Column(modifier = modifier.verticalScroll(rememberScrollState())) {
    Column(modifier = Modifier.padding(start = 24.dp)) {
      Text(
        text = stringResource(id = R.string.ChatFoldersFragment__organize_your_chats),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp, end = 12.dp)
      )
      Text(
        text = stringResource(id = R.string.ChatFoldersFragment__folders),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
      )
      FolderRow(
        icon = R.drawable.symbol_plus_compact_16,
        title = stringResource(R.string.ChatFoldersFragment__create_a_folder),
        onClick = { onFolderClicked(ChatFolderRecord()) }
      )
    }

    val columnHeight = dimensionResource(id = R.dimen.chat_folder_row_height).value * state.folders.size
    LazyColumn(
      modifier = Modifier
        .height(columnHeight.dp)
        .dragContainer(dragDropState),
      state = listState
    ) {
      itemsIndexed(state.folders) { index, folder ->
        DraggableItem(dragDropState, index) { isDragging ->
          val elevation = if (isDragging) 1.dp else 0.dp
          val isAllChats = folder.folderType == ChatFolderRecord.FolderType.ALL
          FolderRow(
            icon = R.drawable.ic_chat_folder_24,
            title = if (isAllChats) stringResource(R.string.ChatFoldersFragment__all_chats) else folder.name,
            subtitle = getFolderDescription(folder),
            onClick = if (!isAllChats) {
              { onFolderClicked(folder) }
            } else null,
            elevation = elevation,
            showDragHandle = true,
            modifier = Modifier.padding(start = 12.dp)
          )
        }
      }
    }

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
          val title: String = stringResource(R.string.ChatFoldersFragment__unreads)
          FolderRow(
            icon = R.drawable.symbol_chat_badge_24,
            title = title,
            subtitle = stringResource(R.string.ChatFoldersFragment__unread_messages),
            onAdd = { onAdd(chatFolder) },
            modifier = Modifier.padding(start = 12.dp)
          )
        }
        ChatFolderRecord.FolderType.INDIVIDUAL -> {
          val title: String = stringResource(R.string.ChatFoldersFragment__one_on_one_chats)
          FolderRow(
            icon = R.drawable.symbol_person_light_24,
            title = title,
            subtitle = stringResource(R.string.ChatFoldersFragment__only_direct_messages),
            onAdd = { onAdd(chatFolder) },
            modifier = Modifier.padding(start = 12.dp)
          )
        }
        ChatFolderRecord.FolderType.GROUP -> {
          val title: String = stringResource(R.string.ChatFoldersFragment__groups)
          FolderRow(
            icon = R.drawable.symbol_group_light_20,
            title = title,
            subtitle = stringResource(R.string.ChatFoldersFragment__only_group_messages),
            onAdd = { onAdd(chatFolder) },
            modifier = Modifier.padding(start = 12.dp)
          )
        }
        ChatFolderRecord.FolderType.ALL -> {
          throw IllegalStateException("All chats should not be suggested")
        }
        ChatFolderRecord.FolderType.CUSTOM -> {
          throw IllegalStateException("Custom folders should not be suggested")
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

@Composable
fun FolderRow(
  modifier: Modifier = Modifier,
  icon: Int,
  title: String,
  subtitle: String = "",
  onClick: (() -> Unit)? = null,
  onAdd: (() -> Unit)? = null,
  elevation: Dp = 0.dp,
  showDragHandle: Boolean = false
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = if (onClick != null) {
      modifier
        .padding(end = 12.dp)
        .clickable(onClick = onClick)
        .fillMaxWidth()
        .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        .shadow(elevation = elevation)
    } else {
      modifier
        .padding(end = 12.dp)
        .fillMaxWidth()
        .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        .shadow(elevation = elevation)
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
