package org.thoughtcrime.securesms.verify

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
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.getSerializableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Bottom sheet info explaining the results of automatic key verification
 */
class EncryptionVerifiedSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.67f

  companion object {

    private const val ARG_STATUS = "arg.status"
    private const val ARG_NAME = "arg.name"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, status: AutomaticVerificationStatus, name: String) {
      EncryptionVerifiedSheet().apply {
        arguments = Bundle().apply {
          putSerializable(ARG_STATUS, status)
          putString(ARG_NAME, name)
        }
        show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      }
    }
  }

  @Composable
  override fun SheetContent() {
    VerifiedSheet(
      verifiedStatus = requireArguments().getSerializableCompat(ARG_STATUS, AutomaticVerificationStatus::class.java)!!,
      name = requireArguments().getString(ARG_NAME, "")
    ) {
      this.dismissAllowingStateLoss()
    }
  }
}

@Composable
fun VerifiedSheet(
  verifiedStatus: AutomaticVerificationStatus = AutomaticVerificationStatus.UNAVAILABLE_TEMPORARY,
  name: String = "",
  onClick: () -> Unit = {}
) {
  val (icon, title, body) = when (verifiedStatus) {
    AutomaticVerificationStatus.VERIFIED -> {
      Triple(
        ImageVector.vectorResource(R.drawable.symbol_check_48),
        stringResource(R.string.EncryptionVerifiedSheet__title_success),
        stringResource(R.string.EncryptionVerifiedSheet__body_success)
      )
    }
    AutomaticVerificationStatus.UNAVAILABLE_PERMANENT -> {
      Triple(
        ImageVector.vectorResource(R.drawable.symbol_info_48),
        stringResource(R.string.EncryptionVerifiedSheet__title_unavailable),
        stringResource(R.string.EncryptionVerifiedSheet__body_unavailable)
      )
    }
    else -> {
      Triple(
        ImageVector.vectorResource(R.drawable.symbol_info_48),
        stringResource(R.string.EncryptionVerifiedSheet__title_no_longer_unavailable),
        stringResource(R.string.EncryptionVerifiedSheet__body_no_longer_unavailable, name)
      )
    }
  }

  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    BottomSheets.Handle()
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(top = 28.dp)
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 12.dp)
    )
    Text(
      text = body,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyMedium
    )
    Buttons.LargeTonal(
      onClick = onClick,
      modifier = Modifier.defaultMinSize(minWidth = 220.dp).padding(vertical = 40.dp)
    ) {
      Text(stringResource(id = android.R.string.ok))
    }
  }
}

@DayNightPreviews
@Composable
fun FinishedSheetSheetPreview() {
  Previews.BottomSheetContentPreview {
    VerifiedSheet()
  }
}
