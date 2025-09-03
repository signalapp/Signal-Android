package org.thoughtcrime.securesms.linkdevice

import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Bottom sheet dialog allowing users to choose whether to transfer their message history
 */
class LinkDeviceSyncBottomSheet : ComposeBottomSheetDialogFragment() {

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  @Composable
  override fun SheetContent() {
    SyncSheet(
      onLink = { shouldSync ->
        viewModel.addDevice(shouldSync)
        findNavController().popBackStack(R.id.linkDeviceFragment, false)
      },
      enabledMediaBackups = SignalStore.backup.backsUpMedia
    )
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    viewModel.onQrCodeDismissed()
  }
}

@Composable
fun SyncSheet(
  onLink: (Boolean) -> Unit,
  enabledMediaBackups: Boolean = false
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(horizontal = 24.dp)
  ) {
    BottomSheets.Handle()

    Spacer(modifier = Modifier.size(12.dp))
    SheetOption(
      painterResource(R.drawable.symbol_chat_check),
      stringResource(R.string.LinkDeviceSyncBottomSheet_transfer),
      if (enabledMediaBackups) {
        stringResource(R.string.LinkDeviceSyncBottomSheet_transfer_your_text_and_all_media)
      } else {
        stringResource(R.string.LinkDeviceSyncBottomSheet_transfer_your_text)
      }
    ) { onLink(true) }

    SheetOption(
      painterResource(R.drawable.symbol_chat_x),
      stringResource(R.string.LinkDeviceSyncBottomSheet_dont_transfer),
      stringResource(R.string.LinkDeviceSyncBottomSheet_no_old_messages)
    ) { onLink(false) }

    Spacer(modifier = Modifier.size(60.dp))
  }
}

@Composable
private fun SheetOption(
  icon: Painter,
  title: String,
  description: String,
  onLink: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp)
      .defaultMinSize(minHeight = 96.dp)
      .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp))
      .clickable { onLink() },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 24.dp, end = 16.dp).size(44.dp)
    )
    Column(
      modifier = Modifier.padding(end = 24.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@SignalPreview
@Composable
fun SyncSheetSheetSheetPreview() {
  Previews.BottomSheetPreview {
    SyncSheet(onLink = {})
  }
}
