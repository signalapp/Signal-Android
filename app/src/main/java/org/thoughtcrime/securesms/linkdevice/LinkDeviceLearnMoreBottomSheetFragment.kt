package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.signal.core.ui.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil

/**
 *  Bottom sheet dialog displayed when users click 'Learn more' when linking a device
 */
class LinkDeviceLearnMoreBottomSheetFragment : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.8f

  companion object {
    const val SIGNAL_DOWNLOAD_URL = "https://signal.org/download"
  }

  @Composable
  override fun SheetContent() {
    LearnMoreSheet()
  }
}

@Composable
fun LearnMoreSheet() {
  val context = LocalContext.current
  val downloadUrl = stringResource(id = R.string.LinkDeviceFragment__signal_download_url)
  val fullString = stringResource(id = R.string.LinkDeviceFragment__on_other_device_visit_signal, downloadUrl)
  val spanned = SpanUtil.urlSubsequence(fullString, downloadUrl, LinkDeviceLearnMoreBottomSheetFragment.SIGNAL_DOWNLOAD_URL)

  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentSize(Alignment.Center)
      .padding(bottom = 48.dp)
  ) {
    BottomSheets.Handle()
    Icon(
      painter = painterResource(R.drawable.ic_all_devices),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.size(110.dp)
    )
    Text(
      style = MaterialTheme.typography.titleLarge,
      text = stringResource(R.string.LinkDeviceFragment__signal_on_desktop_ipad),
      modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
    )
    LinkedDeviceInformationRow(
      painterResource(R.drawable.symbol_lock_24),
      stringResource(R.string.LinkDeviceFragment__all_messaging_is_private)
    )
    LinkedDeviceInformationRow(
      painterResource(R.drawable.ic_replies_outline_20),
      stringResource(R.string.LinkDeviceFragment__signal_messages_are_synchronized)
    )
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 40.dp, end = 32.dp)) {
      Icon(
        painter = painterResource(R.drawable.symbol_save_android_24),
        contentDescription = stringResource(R.string.preferences__linked_devices),
        modifier = Modifier.size(24.dp).padding(top = 4.dp)
      )
      Texts.LinkifiedText(
        textWithUrlSpans = spanned,
        onUrlClick = { CommunicationActions.openBrowserLink(context, it) },
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.padding(start = 20.dp)
      )
    }
  }
}

@Composable
private fun LinkedDeviceInformationRow(
  iconPainter: Painter,
  text: String
) {
  Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 40.dp, end = 32.dp)) {
    Icon(
      painter = iconPainter,
      contentDescription = null,
      modifier = Modifier.size(24.dp).padding(top = 4.dp)
    )
    Text(
      style = MaterialTheme.typography.bodyLarge,
      text = text,
      modifier = Modifier.padding(start = 20.dp)
    )
  }
}

@SignalPreview
@Composable
fun LearnMorePreview() {
  Previews.BottomSheetPreview {
    LearnMoreSheet()
  }
}
