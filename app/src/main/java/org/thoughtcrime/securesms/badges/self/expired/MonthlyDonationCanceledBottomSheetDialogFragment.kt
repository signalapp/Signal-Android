package org.thoughtcrime.securesms.badges.self.expired

import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Texts
import org.signal.core.ui.theme.SignalTheme
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImage112
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.ManageDonationsFragment
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.viewModel

class MonthlyDonationCanceledBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  companion object {

    private const val ARG_IN_APP_PAYMENT_ID = "arg.inAppPaymentId"

    @JvmStatic
    @JvmOverloads
    fun show(fragmentManager: FragmentManager, inAppPaymentId: InAppPaymentTable.InAppPaymentId? = null) {
      val fragment = MonthlyDonationCanceledBottomSheetDialogFragment()
      fragment.arguments = bundleOf(
        ARG_IN_APP_PAYMENT_ID to inAppPaymentId
      )
      fragment.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override val peekHeightPercentage: Float = 1f

  private val viewModel by viewModel {
    MonthlyDonationCanceledViewModel(arguments?.getParcelableCompat(ARG_IN_APP_PAYMENT_ID, InAppPaymentTable.InAppPaymentId::class.java))
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.state

    if (state.loadState == MonthlyDonationCanceledState.LoadState.LOADING) {
      return
    }

    if (state.loadState == MonthlyDonationCanceledState.LoadState.FAILED) {
      LaunchedEffect(Unit) {
        dismissAllowingStateLoss()
      }

      return
    }

    MonthlyDonationCanceled(
      badge = state.badge,
      errorMessageRes = state.errorMessage,
      onRenewClicked = {
        startActivity(AppSettingsActivity.subscriptions(requireContext()))
        dismissAllowingStateLoss()
      },
      onNotNowClicked = {
        SignalStore.inAppPayments.showMonthlyDonationCanceledDialog = false
        dismissAllowingStateLoss()
      }
    )
  }
}

@Preview(name = "Light Theme", group = "ShortName", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "ShortName", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MonthlyDonationCanceledPreview() {
  SignalTheme {
    Surface {
      MonthlyDonationCanceled(
        badge = Badge(
          id = "",
          category = Badge.Category.Donor,
          name = "Signal Star",
          description = "",
          imageUrl = Uri.EMPTY,
          imageDensity = "",
          expirationTimestamp = 0L,
          visible = true,
          duration = 0L
        ),
        errorMessageRes = R.string.StripeFailureCode__verify_your_bank_details_are_correct,
        onRenewClicked = {},
        onNotNowClicked = {}
      )
    }
  }
}

@Composable
private fun MonthlyDonationCanceled(
  badge: Badge?,
  @StringRes errorMessageRes: Int,
  onRenewClicked: () -> Unit,
  onNotNowClicked: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(horizontal = 34.dp)
  ) {
    BottomSheets.Handle()

    if (badge != null) {
      Box(modifier = Modifier.padding(top = 21.dp, bottom = 16.dp)) {
        BadgeImage112(
          badge = badge,
          modifier = Modifier
            .size(80.dp)
        )

        Image(
          imageVector = ImageVector.vectorResource(id = R.drawable.symbol_error_circle_fill_24),
          contentScale = ContentScale.Inside,
          contentDescription = null,
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
          modifier = Modifier
            .size(24.dp)
            .align(Alignment.TopEnd)
            .background(
              color = SignalTheme.colors.colorSurface1,
              shape = CircleShape
            )
        )
      }
    }

    Text(
      text = stringResource(id = R.string.MonthlyDonationCanceled__title),
      style = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface),
      modifier = Modifier.padding(bottom = 20.dp)
    )

    val context = LocalContext.current
    val learnMore = stringResource(id = R.string.MonthlyDonationCanceled__learn_more)
    val errorMessage = stringResource(id = errorMessageRes)
    val fullString = stringResource(id = R.string.MonthlyDonationCanceled__message, errorMessage, learnMore)
    val spanned = SpanUtil.urlSubsequence(fullString, learnMore, ManageDonationsFragment.DONATE_TROUBLESHOOTING_URL)
    Texts.LinkifiedText(
      textWithUrlSpans = spanned,
      onUrlClick = { CommunicationActions.openBrowserLink(context, it) },
      style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant),
      modifier = Modifier.padding(bottom = 36.dp)
    )

    Buttons.LargeTonal(
      onClick = onRenewClicked,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 16.dp)
    ) {
      Text(text = stringResource(id = R.string.MonthlyDonationCanceled__renew_button))
    }

    TextButton(
      onClick = onNotNowClicked,
      modifier = Modifier
        .defaultMinSize(minWidth = 220.dp)
        .padding(bottom = 56.dp)
    ) {
      Text(text = stringResource(id = R.string.MonthlyDonationCanceled__not_now_button))
    }
  }
}
