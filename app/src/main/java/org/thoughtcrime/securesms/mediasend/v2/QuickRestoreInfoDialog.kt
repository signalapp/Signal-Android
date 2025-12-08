package org.thoughtcrime.securesms.mediasend.v2

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Bottom sheet dialog displayed when users scan a quick restore with the system camera and then
 * follow the prompt into the Signal camera to scan the qr code a second time from within Signal.
 */
class QuickRestoreInfoDialog : ComposeBottomSheetDialogFragment() {

  companion object {
    fun show(fragmentManager: FragmentManager) {
      QuickRestoreInfoDialog().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    InfoSheet(this::dismissAllowingStateLoss)
  }
}

@Composable
private fun InfoSheet(onClick: () -> Unit) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp)
  ) {
    BottomSheets.Handle()

    Image(
      imageVector = ImageVector.vectorResource(R.drawable.quick_restore),
      contentDescription = null,
      modifier = Modifier.padding(top = 14.dp, bottom = 24.dp)
    )
    Text(
      text = stringResource(R.string.QuickRestoreInfoDialog__scan_qr_code),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
      text = stringResource(R.string.QuickRestoreInfoDialog__use_this_device_to_scan_qr_code),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 28.dp)
    )
    Buttons.LargeTonal(
      onClick = onClick,
      modifier = Modifier.defaultMinSize(minWidth = 220.dp)
    ) {
      Text(stringResource(id = R.string.QuickRestoreInfoDialog__okay))
    }
  }
}

@DayNightPreviews
@Composable
fun InfoSheetPreview() {
  Previews.BottomSheetPreview {
    InfoSheet(onClick = {})
  }
}
