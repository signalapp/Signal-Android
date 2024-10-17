package org.thoughtcrime.securesms.linkdevice

import android.content.DialogInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Education sheet shown before biometrics when linking a device
 */
class LinkDeviceEducationSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.67f

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  @Composable
  override fun SheetContent() {
    DeviceEducationSheet(this::onDismiss)
  }

  override fun onCancel(dialog: DialogInterface) {
    viewModel.markEducationSheetSeen(true)
    super.onCancel(dialog)
  }

  fun onDismiss() {
    viewModel.markEducationSheetSeen(true)
    dismissAllowingStateLoss()
  }
}

@Composable
private fun DeviceEducationSheet(onClick: () -> Unit) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()
    Icon(
      painter = painterResource(R.drawable.ic_phone_lock),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(top = 24.dp)
    )

    Text(
      text = stringResource(R.string.LinkDeviceFragment__before_linking),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = stringResource(R.string.LinkDeviceFragment__tap_continue_and_enter_phone),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(horizontal = 44.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Buttons.LargeTonal(
      onClick = onClick,
      modifier = Modifier.defaultMinSize(minWidth = 220.dp).padding(top = 28.dp, bottom = 56.dp)
    ) {
      Text(stringResource(id = R.string.LinkDeviceFragment__continue))
    }
  }
}

@SignalPreview
@Composable
fun DeviceEducationSheetPreview() {
  Previews.BottomSheetPreview {
    DeviceEducationSheet(onClick = {})
  }
}
