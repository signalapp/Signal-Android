package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Bottom sheet dialog displayed when users click 'Link a device'
 */
class LinkDeviceIntroBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.8f

  @Composable
  override fun SheetContent() {
    EducationSheet(this::dismissAllowingStateLoss)
  }
}

@Composable
fun EducationSheet(onClick: () -> Unit) {
  val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.linking_device))

  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp)
  ) {
    BottomSheets.Handle()
    Box(modifier = Modifier.size(150.dp)) {
      LottieAnimation(composition, iterations = LottieConstants.IterateForever, modifier = Modifier.matchParentSize())
    }
    Text(
      text = stringResource(R.string.AddLinkDeviceFragment__scan_qr_code),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = 12.dp)
    )
    Text(
      text = stringResource(R.string.AddLinkDeviceFragment__use_this_device_to_scan_qr_code),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(bottom = 12.dp)
    )
    Buttons.LargeTonal(
      onClick = onClick,
      modifier = Modifier.defaultMinSize(minWidth = 220.dp)
    ) {
      Text(stringResource(id = R.string.AddLinkDeviceFragment__okay))
    }
  }
}

@SignalPreview
@Composable
fun EducationSheetPreview() {
  Previews.BottomSheetPreview {
    EducationSheet(onClick = {})
  }
}
