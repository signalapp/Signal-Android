package org.thoughtcrime.securesms.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * A bottom sheet that warns the user when they haven't been able to connect to the websocket for some time.
 */
class ConnectivityWarningBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.66f

  companion object {

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      if (fragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG) == null) {
        ConnectivityWarningBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        SignalStore.misc.lastConnectivityWarningTime = System.currentTimeMillis()
      }
    }
  }

  @Composable
  override fun SheetContent() {
    Sheet(
      onDismiss = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
private fun Sheet(onDismiss: () -> Unit = {}) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)
  ) {
    BottomSheets.Handle()
    Icon(
      painterResource(id = R.drawable.ic_connectivity_warning),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
    )
    Text(
      text = stringResource(id = R.string.ConnectivityWarningBottomSheet_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
    Text(
      text = stringResource(id = R.string.ConnectivityWarningBottomSheet_body),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 24.dp)
    )
    Row(
      modifier = Modifier.padding(top = 60.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
      Buttons.MediumTonal(
        onClick = onDismiss,
        modifier = Modifier.padding(end = 12.dp)
      ) {
        Text(stringResource(id = R.string.ConnectivityWarningBottomSheet_dismiss_button))
      }
    }
  }
}

@SignalPreview
@Composable
private fun ConnectivityWarningSheetPreview() {
  Previews.BottomSheetPreview {
    Sheet()
  }
}
