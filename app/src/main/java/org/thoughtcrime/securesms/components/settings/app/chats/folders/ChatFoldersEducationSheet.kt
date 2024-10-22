package org.thoughtcrime.securesms.components.settings.app.chats.folders

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Education sheet shown when clicking on chat folders for the first time
 */
class ChatFoldersEducationSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.8f

  @Composable
  override fun SheetContent() {
    FolderEducationSheet(this::dismissAllowingStateLoss)
  }
}

@Composable
private fun FolderEducationSheet(onClick: () -> Unit) {
  return Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp)
  ) {
    BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

    Image(
      painter = painterResource(R.drawable.image_folder_sheet),
      contentDescription = null,
      modifier = Modifier.padding(top = 36.dp).align(Alignment.CenterHorizontally)
    )

    Text(
      text = stringResource(R.string.ChatsSettingsFragment__chat_folders),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 18.dp, bottom = 12.dp).align(Alignment.CenterHorizontally),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = stringResource(R.string.ChatFoldersFragment__organize_your_chats),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(bottom = 28.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    EducationRow(
      text = stringResource(R.string.ChatFoldersEducationSheet__create_folders_for_family),
      painter = painterResource(R.drawable.symbol_folder_24)
    )

    EducationRow(
      text = stringResource(R.string.ChatFoldersEducationSheet__choose_to_show_unread),
      painter = painterResource(R.drawable.symbol_chat_badge_24)
    )

    EducationRow(
      text = stringResource(R.string.ChatFoldersEducationSheet__easily_add_suggested),
      painter = painterResource(R.drawable.symbol_plus_circle_24)
    )

    Buttons.LargeTonal(
      onClick = onClick,
      modifier = Modifier.defaultMinSize(minWidth = 220.dp).padding(top = 52.dp, bottom = 30.dp).align(Alignment.CenterHorizontally)
    ) {
      Text(stringResource(id = R.string.ChatFoldersEducationSheet__continue))
    }
  }
}

@Composable
fun EducationRow(text: String, painter: Painter) {
  Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 20.dp)) {
    Icon(
      painter = painter,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp)
    )
  }
}

@SignalPreview
@Composable
fun ChatFoldersEducationSheetPreview() {
  Previews.BottomSheetPreview {
    FolderEducationSheet(onClick = {})
  }
}
