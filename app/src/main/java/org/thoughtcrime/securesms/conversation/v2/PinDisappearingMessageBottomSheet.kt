package org.thoughtcrime.securesms.conversation.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Bottom sheet informing users about pinning disappearing messages
 */
class PinDisappearingMessageBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      PinDisappearingMessageBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    PinDisappearingSheet(
      onDismiss = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
fun PinDisappearingSheet(
  onDismiss: () -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    BottomSheets.Handle()

    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_timer_80),
      contentDescription = stringResource(R.string.PinnedMessage__disappearing_message_content_description),
      modifier = Modifier.padding(vertical = 24.dp),
      tint = Color.Unspecified
    )

    Text(
      text = stringResource(R.string.PinnedMessage__disappearing_message_title),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(bottom = 16.dp),
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )

    Text(
      text = stringResource(R.string.PinnedMessage__disappearing_message_body),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(bottom = 4.dp),
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Buttons.LargeTonal(
      onClick = onDismiss,
      modifier = Modifier.padding(top = 40.dp, bottom = 56.dp)
    ) {
      Text(stringResource(id = R.string.PinnedMessage__got_it))
    }
  }
}

@DayNightPreviews
@Composable
private fun PinnedDialogPreview() {
  Previews.Preview {
    PinDisappearingSheet()
  }
}
