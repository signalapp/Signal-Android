package org.thoughtcrime.securesms.conversationlist

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
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

    @JvmStatic
    fun showChatFolderSheet(folders: List<ChatFolderRecord>, threadId: Long): ComposeBottomSheetDialogFragment {
      return AddToFolderBottomSheet().apply {
        arguments = bundleOf(
          ARG_FOLDERS to folders,
          ARG_THREAD_ID to threadId
        )
      }
    }
  }

  @Composable
  override fun SheetContent() {
    val folders = arguments?.getParcelableArrayListCompat(ARG_FOLDERS, ChatFolderRecord::class.java)?.filter { it.folderType != ChatFolderRecord.FolderType.ALL }
    val threadId = arguments?.getLong(ARG_THREAD_ID)

    AddToChatFolderSheetContent(
      folders = remember { folders ?: emptyList() },
      onClick = { folder ->
        if (threadId != null) {
          viewModel.addToFolder(folder.id, threadId)
          Toast.makeText(context, requireContext().getString(R.string.AddToFolderBottomSheet_added_to_s, folder.name), Toast.LENGTH_SHORT).show()
        }
        dismissAllowingStateLoss()
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
  folders: List<ChatFolderRecord>,
  onClick: (ChatFolderRecord) -> Unit = {},
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .clickable(onClick = { onClick(folder) })
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
      folders = listOf(ChatFolderRecord(name = "Friends"), ChatFolderRecord(name = "Work"))
    )
  }
}
