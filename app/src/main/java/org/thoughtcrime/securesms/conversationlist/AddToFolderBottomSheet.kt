package org.thoughtcrime.securesms.conversationlist

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.util.getParcelableArrayListCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.viewModel

/**
 * Bottom sheet shown when choosing to add a chat to a folder
 */
class AddToFolderBottomSheet private constructor() : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  private val viewModel by viewModel { ConversationListViewModel(isArchived = false) }

  companion object {
    private const val ARG_FOLDERS = "argument.folders"
    private const val ARG_THREAD_ID = "argument.thread.id"
    private const val ARG_IS_INDIVIDUAL_CHAT = "argument.is.individual.chat"

    /**
     * Shows a bottom sheet that allows a thread to be added to a folder.
     *
     * @param folders list of available folders to add a thread to
     * @param threadId the thread that is going to be added
     * @param isIndividualChat whether the thread is an individual/1:1 chat as opposed to a group chat
     */
    @JvmStatic
    fun showChatFolderSheet(folders: List<ChatFolderRecord>, threadId: Long, isIndividualChat: Boolean): ComposeBottomSheetDialogFragment {
      return AddToFolderBottomSheet().apply {
        arguments = bundleOf(
          ARG_FOLDERS to folders,
          ARG_THREAD_ID to threadId,
          ARG_IS_INDIVIDUAL_CHAT to isIndividualChat
        )
      }
    }
  }

  @Composable
  override fun SheetContent() {
    val folders = requireArguments().getParcelableArrayListCompat(ARG_FOLDERS, ChatFolderRecord::class.java)?.filter { it.folderType != ChatFolderRecord.FolderType.ALL }
    val threadId = requireArguments().getLong(ARG_THREAD_ID)
    val isIndividualChat = requireArguments().getBoolean(ARG_IS_INDIVIDUAL_CHAT)

    AddToChatFolderSheetContent(
      threadId = threadId,
      isIndividualChat = isIndividualChat,
      folders = remember { folders ?: emptyList() },
      onClick = { folder, isAlreadyAdded ->
        if (isAlreadyAdded) {
          Toast.makeText(context, requireContext().getString(R.string.AddToFolderBottomSheet_this_chat_is_already, folder.name), Toast.LENGTH_SHORT).show()
        } else {
          viewModel.addToFolder(folder.id, threadId)
          Toast.makeText(context, requireContext().getString(R.string.AddToFolderBottomSheet_added_to_s, folder.name), Toast.LENGTH_SHORT).show()
          dismissAllowingStateLoss()
        }
      },
      onCreate = {
        requireContext().startActivity(AppSettingsActivity.createChatFolder(requireContext(), -1, threadId))
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun AddToChatFolderSheetContent(
  threadId: Long,
  isIndividualChat: Boolean,
  folders: List<ChatFolderRecord>,
  onClick: (ChatFolderRecord, Boolean) -> Unit = { _, _ -> },
  onCreate: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    BottomSheets.Handle()

    Text(
      text = stringResource(R.string.AddToFolderBottomSheet_choose_a_folder),
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 30.dp)
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 60.dp)
        .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp))
    ) {
      items(folders) { folder ->
        val isIncludedViaChatType = (isIndividualChat && folder.showIndividualChats) || (!isIndividualChat && folder.showGroupChats)
        val isIncludedExplicitly = folder.includedChats.contains(threadId)
        val isExcludedExplicitly = folder.excludedChats.contains(threadId)

        val isAlreadyAdded = (isIncludedExplicitly || isIncludedViaChatType) && !isExcludedExplicitly

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(onClick = { onClick(folder, isAlreadyAdded) })
            .padding(start = 24.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        ) {
          Image(
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            imageVector = ImageVector.vectorResource(id = R.drawable.symbol_folder_24),
            contentDescription = null,
            modifier = Modifier
              .size(40.dp)
              .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
              .padding(8.dp)
          )

          Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
          )

          Spacer(modifier = Modifier.weight(1f))

          if (isAlreadyAdded) {
            Icon(
              painterResource(R.drawable.symbol_check_white_24),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp)
                .background(
                  color = Color.Black.copy(.40f),
                  shape = CircleShape
                )
                .padding(4.dp)
            )
          }
        }
      }

      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(onClick = onCreate)
            .padding(start = 24.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimensionResource(id = R.dimen.chat_folder_row_height))
        ) {
          Image(
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
            imageVector = ImageVector.vectorResource(id = R.drawable.symbol_plus_24),
            contentDescription = null,
            modifier = Modifier
              .size(40.dp)
              .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
              .padding(8.dp)
          )

          Text(
            text = stringResource(id = R.string.ChatFoldersFragment__create_a_folder),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
          )
        }
      }
    }
  }
}

@SignalPreview
@Composable
private fun AddToChatFolderSheetContentPreview() {
  Previews.BottomSheetPreview {
    AddToChatFolderSheetContent(
      folders = listOf(ChatFolderRecord(name = "Friends"), ChatFolderRecord(name = "Work")),
      threadId = 1,
      isIndividualChat = false
    )
  }
}
