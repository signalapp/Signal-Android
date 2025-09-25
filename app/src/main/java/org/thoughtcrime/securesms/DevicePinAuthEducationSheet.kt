package org.thoughtcrime.securesms

import android.content.DialogInterface
import android.os.Bundle
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
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Education sheet shown before authentication explaining that users should use their device credentials
 */
class DevicePinAuthEducationSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.67f

  companion object {
    const val REQUEST_KEY = "DevicePinAuthEducationSheet"

    private const val ARG_TITLE = "arg.title"

    @JvmStatic
    fun show(title: String, fragmentManager: FragmentManager) {
      DevicePinAuthEducationSheet().apply {
        arguments = Bundle().apply {
          putString(ARG_TITLE, title)
        }
      }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  private val title: String
    get() = requireArguments().getString(ARG_TITLE)!!

  override fun onDismiss(dialog: DialogInterface) {
    setFragmentResult(REQUEST_KEY, Bundle())
    super.onDismiss(dialog)
  }

  @Composable
  override fun SheetContent() {
    DevicePinAuthEducationSheet(
      title = title,
      onClick = { dismissAllowingStateLoss() }
    )
  }
}

@Composable
fun DevicePinAuthEducationSheet(
  title: String,
  onClick: () -> Unit
) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()
    Icon(
      painter = painterResource(R.drawable.phone_lock),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(top = 24.dp)
    )

    Text(
      text = title,
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
fun DevicePinAuthEducationSheetPreview() {
  Previews.BottomSheetPreview {
    DevicePinAuthEducationSheet(
      title = "To continue, confirm it's you",
      onClick = {}
    )
  }
}
