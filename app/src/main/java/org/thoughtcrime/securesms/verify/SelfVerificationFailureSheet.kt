package org.thoughtcrime.securesms.verify

import android.content.DialogInterface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

/**
 * Sheet to prompt for debug logs when self key transparency fails
 */
class SelfVerificationFailureSheet : ComposeBottomSheetDialogFragment() {

  private val viewModel: SelfVerificationFailureViewModel by viewModels()
  override val peekHeightPercentage: Float = 0.75f

  companion object {

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      SignalStore.misc.hasSeenKeyTransparencyFailure = true
      SelfVerificationFailureSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.sendEmail) {
      if (state.sendEmail && state.debugLogUrl != null) {
        val subject = context.getString(R.string.SelfVerificationFailureSheet__email_subject)
        val prefix = "\n${context.getString(R.string.HelpFragment__debug_log)} ${state.debugLogUrl}\n\n"
        val body = SupportEmailUtil.generateSupportEmailBody(context, R.string.SelfVerificationFailureSheet__email_filter, prefix, null)
        CommunicationActions.openEmail(context, SupportEmailUtil.getSupportEmailAddress(context), subject, body)
        dismissAllowingStateLoss()
      } else if (state.sendEmail) {
        Toast.makeText(requireContext(), getString(R.string.HelpFragment__could_not_upload_logs), Toast.LENGTH_LONG).show()
        dismissAllowingStateLoss()
      }
    }

    VerifyFailureSheet(
      state,
      onLearnMoreClicked = {
        CommunicationActions.openBrowserLink(requireContext(), getString(R.string.HelpFragment__link__debug_info))
      },
      onDismiss = {
        dismissAllowingStateLoss()
      },
      onSubmit = {
        viewModel.submitLogs()
      }
    )
  }
}

@Composable
fun VerifyFailureSheet(
  state: VerificationUiState,
  onLearnMoreClicked: () -> Unit = {},
  onDismiss: () -> Unit = {},
  onSubmit: () -> Unit = {}
) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    BottomSheets.Handle()
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_error_circle_24),
      contentDescription = null,
      tint = Color(0xFFC88600),
      modifier = Modifier
        .padding(top = 24.dp, bottom = 8.dp)
        .size(66.dp)
        .background(color = Color(0xFFF9E4B6), shape = CircleShape)
        .padding(12.dp)
    )

    Text(
      text = stringResource(R.string.SelfVerificationFailureSheet__title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(vertical = 12.dp),
      color = MaterialTheme.colorScheme.onSurface
    )

    Text(
      text = buildAnnotatedString {
        append(stringResource(id = R.string.SelfVerificationFailureSheet__body))
        append(" ")

        withLink(
          LinkAnnotation.Clickable(tag = "learn-more") { onLearnMoreClicked() }
        ) {
          withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append(stringResource(id = R.string.SelfVerificationFailureSheet__learn_more))
          }
        }
      },
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 54.dp, bottom = 28.dp)
    ) {
      Buttons.LargeTonal(
        onClick = onDismiss,
        modifier = Modifier.weight(1f)
      ) {
        Text(stringResource(id = R.string.SelfVerificationFailureSheet__no_thanks))
      }
      Spacer(modifier = Modifier.size(12.dp))
      Buttons.LargeTonal(
        onClick = if (state.showAsProgress) {
          {}
        } else {
          onSubmit
        },
        modifier = Modifier.weight(1f)
      ) {
        if (state.showAsProgress) {
          CircularProgressIndicator(
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
          )
        } else {
          Text(stringResource(id = R.string.SelfVerificationFailureSheet__submit))
        }
      }
    }
  }
}

@DayNightPreviews
@Composable
fun VerifyFailureSheetPreview() {
  Previews.BottomSheetContentPreview {
    VerifyFailureSheet(state = VerificationUiState())
  }
}
