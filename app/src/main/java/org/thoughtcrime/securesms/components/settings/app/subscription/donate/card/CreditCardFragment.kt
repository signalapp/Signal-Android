package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationSerializationHelper.toFiatMoney
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorActionResult
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe.StripePaymentInProgressViewModel
import org.thoughtcrime.securesms.databinding.CreditCardFragmentBinding
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class CreditCardFragment : Fragment(R.layout.credit_card_fragment) {

  private val binding by ViewBinderDelegate(CreditCardFragmentBinding::bind)
  private val args: CreditCardFragmentArgs by navArgs()
  private val viewModel: CreditCardViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()
  private val stripePaymentViewModel: StripePaymentInProgressViewModel by navGraphViewModels(
    R.id.checkout_flow,
    factoryProducer = {
      StripePaymentInProgressViewModel.Factory(requireListener<InAppPaymentComponent>().stripeRepository)
    }
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    TemporaryScreenshotSecurity.bindToViewLifecycleOwner(this)
    InAppPaymentCheckoutDelegate.ErrorHandler().attach(this, null, args.inAppPayment.id)

    setFragmentResultListener(StripePaymentInProgressFragment.REQUEST_KEY) { _, bundle ->
      val result: InAppPaymentProcessorActionResult = bundle.getParcelableCompat(StripePaymentInProgressFragment.REQUEST_KEY, InAppPaymentProcessorActionResult::class.java)!!
      if (result.status == InAppPaymentProcessorActionResult.Status.SUCCESS) {
        findNavController().popBackStack()
        setFragmentResult(REQUEST_KEY, bundle)
      }
    }

    binding.continueButton.text = when (args.inAppPayment.type) {
      InAppPaymentType.RECURRING_DONATION -> {
        getString(
          R.string.CreditCardFragment__donate_s_month,
          FiatMoneyUtil.format(resources, args.inAppPayment.data.amount!!.toFiatMoney(), FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
        )
      }
      InAppPaymentType.RECURRING_BACKUP -> {
        getString(
          R.string.CreditCardFragment__pay_s_month,
          FiatMoneyUtil.format(resources, args.inAppPayment.data.amount!!.toFiatMoney(), FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
        )
      }
      else -> {
        getString(R.string.CreditCardFragment__donate_s, FiatMoneyUtil.format(resources, args.inAppPayment.data.amount!!.toFiatMoney()))
      }
    }

    binding.description.setLinkColor(ContextCompat.getColor(requireContext(), R.color.signal_colorPrimary))
    binding.description.setLearnMoreVisible(true)
    binding.description.setOnLinkClickListener {
      findNavController().safeNavigate(CreditCardFragmentDirections.actionCreditCardFragmentToYourInformationIsPrivateBottomSheet())
    }

    binding.cardNumber.addTextChangedListener(afterTextChanged = {
      viewModel.onNumberChanged(it?.toString()?.filter { it != ' ' } ?: "")
    })

    binding.cardNumber.addTextChangedListener(CreditCardTextWatcher())

    binding.cardNumber.setOnFocusChangeListener { _, hasFocus ->
      viewModel.onNumberFocusChanged(hasFocus)
    }

    binding.cardCvv.addTextChangedListener(afterTextChanged = {
      viewModel.onCodeChanged(it?.toString() ?: "")
    })

    binding.cardCvv.setOnFocusChangeListener { _, hasFocus ->
      viewModel.onCodeFocusChanged(hasFocus)
    }

    binding.cardCvv.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE && binding.continueButton.isEnabled) {
        binding.continueButton.performClick()
        true
      } else {
        false
      }
    }

    binding.cardExpiry.addTextChangedListener(afterTextChanged = {
      viewModel.onExpirationChanged(it?.toString() ?: "")
    })

    binding.cardExpiry.addTextChangedListener(CreditCardExpirationTextWatcher())

    binding.cardExpiry.setOnFocusChangeListener { _, hasFocus ->
      viewModel.onExpirationFocusChanged(hasFocus)
    }

    binding.continueButton.setOnClickListener {
      stripePaymentViewModel.provideCardData(viewModel.getCardData())
      findNavController().safeNavigate(
        CreditCardFragmentDirections.actionCreditCardFragmentToStripePaymentInProgressFragment(
          InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
          args.inAppPayment,
          args.inAppPayment.type
        )
      )
    }

    binding.toolbar.setNavigationOnClickListener {
      findNavController().popBackStack()
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += viewModel.state.subscribe {
      // TODO [alex] -- type
      presentContinue(it)
      presentCardNumberWrapper(it.numberValidity)
      presentCardExpiryWrapper(it.expirationValidity)
      presentCardCodeWrapper(it.codeValidity)
    }
  }

  override fun onResume() {
    super.onResume()

    when (viewModel.currentFocusField) {
      CreditCardFormState.FocusedField.NONE -> ViewUtil.focusAndShowKeyboard(binding.cardNumber)
      CreditCardFormState.FocusedField.NUMBER -> ViewUtil.focusAndShowKeyboard(binding.cardNumber)
      CreditCardFormState.FocusedField.EXPIRATION -> ViewUtil.focusAndShowKeyboard(binding.cardExpiry)
      CreditCardFormState.FocusedField.CODE -> ViewUtil.focusAndShowKeyboard(binding.cardCvv)
    }
  }

  private fun presentContinue(state: CreditCardValidationState) {
    binding.continueButton.isEnabled = state.isValid
  }

  private fun presentCardNumberWrapper(validity: CreditCardNumberValidator.Validity) {
    val errorState = when (validity) {
      CreditCardNumberValidator.Validity.INVALID -> ErrorState(messageResId = R.string.CreditCardFragment__invalid_card_number)
      CreditCardNumberValidator.Validity.POTENTIALLY_VALID -> NO_ERROR
      CreditCardNumberValidator.Validity.FULLY_VALID -> NO_ERROR
    }

    binding.cardNumberWrapper.error = errorState.resolveErrorText(requireContext())
  }

  private fun presentCardExpiryWrapper(validity: CreditCardExpirationValidator.Validity) {
    val errorState = when (validity) {
      CreditCardExpirationValidator.Validity.INVALID_EXPIRED -> ErrorState(messageResId = R.string.CreditCardFragment__card_has_expired)
      CreditCardExpirationValidator.Validity.INVALID_MISSING_YEAR -> ErrorState(messageResId = R.string.CreditCardFragment__year_required)
      CreditCardExpirationValidator.Validity.INVALID_MONTH -> ErrorState(messageResId = R.string.CreditCardFragment__invalid_month)
      CreditCardExpirationValidator.Validity.INVALID_YEAR -> ErrorState(messageResId = R.string.CreditCardFragment__invalid_year)
      CreditCardExpirationValidator.Validity.POTENTIALLY_VALID -> NO_ERROR
      CreditCardExpirationValidator.Validity.FULLY_VALID -> {
        if (binding.cardExpiry.isFocused) {
          binding.cardCvv.requestFocus()
        }

        NO_ERROR
      }
    }

    binding.cardExpiryWrapper.error = errorState.resolveErrorText(requireContext())
  }

  private fun presentCardCodeWrapper(validity: CreditCardCodeValidator.Validity) {
    val errorState = when (validity) {
      CreditCardCodeValidator.Validity.TOO_LONG -> ErrorState(messageResId = R.string.CreditCardFragment__code_is_too_long)
      CreditCardCodeValidator.Validity.TOO_SHORT -> ErrorState(messageResId = R.string.CreditCardFragment__code_is_too_short)
      CreditCardCodeValidator.Validity.INVALID_CHARACTERS -> ErrorState(messageResId = R.string.CreditCardFragment__invalid_code)
      CreditCardCodeValidator.Validity.POTENTIALLY_VALID -> NO_ERROR
      CreditCardCodeValidator.Validity.FULLY_VALID -> NO_ERROR
    }

    binding.cardCvvWrapper.error = errorState.resolveErrorText(requireContext())
  }

  private data class ErrorState(
    private val isEnabled: Boolean = true,
    @StringRes private val messageResId: Int
  ) {
    fun resolveErrorText(context: Context): String? {
      return if (isEnabled) {
        context.getString(messageResId)
      } else {
        null
      }
    }
  }

  companion object {
    const val REQUEST_KEY = "card.result"

    private val NO_ERROR = ErrorState(false, -1)
  }
}
