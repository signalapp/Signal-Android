package org.thoughtcrime.securesms.verify

import android.content.DialogInterface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Education sheet explaining that conversations now have auto verification
 */
class VerifyAutomaticallyEducationSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.75f

  companion object {

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      VerifyAutomaticallyEducationSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    SignalStore.uiHints.setSeenVerifyAutomaticallySheet()
  }

  @Composable
  override fun SheetContent() {
    VerifyEducationSheet(
      onVerify = {}, // TODO(michelle): Plug in to verify fragment
      onLearnMore = {} // TODO(michelle): Update with support url
    )
  }
}

@Composable
fun VerifyEducationSheet(
  onVerify: () -> Unit = {},
  onLearnMore: () -> Unit = {}
) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()
    Icon(
      painter = painterResource(R.drawable.image_verify_successful),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )

    Text(
      text = stringResource(R.string.VerifyAutomaticallyEducationSheet__title),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 32.dp),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = stringResource(R.string.VerifyAutomaticallyEducationSheet__body),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(horizontal = 32.dp),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 60.dp, bottom = 28.dp)
    ) {
      TextButton(
        onClick = onLearnMore
      ) {
        Text(
          text = stringResource(id = R.string.VerifyAutomaticallyEducationSheet__learn_more)
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      Buttons.LargeTonal(
        onClick = onVerify
      ) {
        Text(stringResource(id = R.string.VerifyAutomaticallyEducationSheet__verify))
      }
    }
  }
}

@DayNightPreviews
@Composable
fun VerifyAutomaticallyEducationSheetPreview() {
  Previews.BottomSheetContentPreview {
    VerifyEducationSheet()
  }
}
