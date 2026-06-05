package org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.util.dp
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.database.model.databaseprotos.InAppPaymentData
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.viewModel
import org.signal.core.ui.R as CoreUiR

/**
 * Entry point to capturing the necessary payment token to pay for a donation
 */
class GatewaySelectorBottomSheet : ComposeBottomSheetDialogFragment() {

  private val args: GatewaySelectorBottomSheetArgs by navArgs()
  override val peekHeightPercentage: Float = 1f

  private val viewModel: GatewaySelectorViewModel by viewModel {
    GatewaySelectorViewModel(args, requireListener<GooglePayComponent>().googlePayRepository)
  }

  @Composable
  override fun SheetContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GatewaySelectorBottomSheetContent(state, onEvent = this::onEvent)
  }

  private fun onEvent(event: GatewaySelectorBottomSheetEvent) {
    when (event) {
      GatewaySelectorBottomSheetEvent.GOOGLE_PAY_SELECTED -> {
        setPaymentMethodAndDismiss(InAppPaymentData.PaymentMethodType.GOOGLE_PAY)
      }

      GatewaySelectorBottomSheetEvent.PAYPAL_SELECTED -> {
        setPaymentMethodAndDismiss(InAppPaymentData.PaymentMethodType.PAYPAL)
      }

      GatewaySelectorBottomSheetEvent.SEPA_SELECTED -> {
        if (viewModel.checkIsSepaPaymentValidAmount()) {
          setPaymentMethodAndDismiss(InAppPaymentData.PaymentMethodType.SEPA_DEBIT)
        } else {
          findNavController().popBackStack()
          setFragmentResult(REQUEST_KEY, bundleOf(FAILURE_KEY to true, SEPA_EURO_MAX to viewModel.getSepaMaximum()))
        }
      }

      GatewaySelectorBottomSheetEvent.IDEAL_SELECTED -> {
        setPaymentMethodAndDismiss(InAppPaymentData.PaymentMethodType.IDEAL)
      }

      GatewaySelectorBottomSheetEvent.CREDIT_CARD_SELECTED -> {
        setPaymentMethodAndDismiss(InAppPaymentData.PaymentMethodType.CARD)
      }
    }
  }

  private fun setPaymentMethodAndDismiss(type: InAppPaymentData.PaymentMethodType) {
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
      val inAppPayment = viewModel.updateInAppPaymentMethod(type)
      findNavController().popBackStack()
      setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to inAppPayment))
    }
  }

  companion object {
    const val REQUEST_KEY = "payment_checkout_mode"
    const val FAILURE_KEY = "gateway_failure"
    const val SEPA_EURO_MAX = "sepa_euro_max"

    fun DSLConfiguration.presentTitleAndSubtitle(context: Context, inAppPayment: InAppPaymentTable.InAppPayment) {
      when (inAppPayment.type) {
        InAppPaymentType.UNKNOWN -> error("Unsupported type UNKNOWN")
        InAppPaymentType.RECURRING_BACKUP -> error("This type is not supported")
        InAppPaymentType.RECURRING_DONATION -> presentMonthlyText(context, inAppPayment)
        InAppPaymentType.ONE_TIME_DONATION -> presentOneTimeText(context, inAppPayment)
        InAppPaymentType.ONE_TIME_GIFT -> presentGiftText(context, inAppPayment)
      }
    }

    private fun DSLConfiguration.presentMonthlyText(context: Context, inAppPayment: InAppPaymentTable.InAppPayment) {
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__donate_s_month_to_signal, FiatMoneyUtil.format(context.resources, inAppPayment.data.amount!!.toFiatMoney())),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleLargeModifier
        )
      )
      space(6.dp)
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__get_a_s_badge, inAppPayment.data.badge!!.name),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.BodyLargeModifier,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant))
        )
      )
    }

    private fun DSLConfiguration.presentOneTimeText(context: Context, inAppPayment: InAppPaymentTable.InAppPayment) {
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, FiatMoneyUtil.format(context.resources, inAppPayment.data.amount!!.toFiatMoney())),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleLargeModifier
        )
      )
      space(6.dp)
      noPadTextPref(
        title = DSLSettingsText.from(
          context.resources.getQuantityString(R.plurals.GatewaySelectorBottomSheet__get_a_s_badge_for_d_days, 30, inAppPayment.data.badge!!.name, 30),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.BodyLargeModifier,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant))
        )
      )
    }

    private fun DSLConfiguration.presentGiftText(context: Context, inAppPayment: InAppPaymentTable.InAppPayment) {
      noPadTextPref(
        title = DSLSettingsText.from(
          context.getString(R.string.GatewaySelectorBottomSheet__donate_s_to_signal, FiatMoneyUtil.format(context.resources, inAppPayment.data.amount!!.toFiatMoney())),
          DSLSettingsText.CenterModifier,
          DSLSettingsText.TitleLargeModifier
        )
      )
      space(6.dp)
      noPadTextPref(
        title = DSLSettingsText.from(
          R.string.GatewaySelectorBottomSheet__donate_for_a_friend,
          DSLSettingsText.CenterModifier,
          DSLSettingsText.BodyLargeModifier,
          DSLSettingsText.ColorModifier(ContextCompat.getColor(context, CoreUiR.color.signal_colorOnSurfaceVariant))
        )
      )
    }
  }
}
