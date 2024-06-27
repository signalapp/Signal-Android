package org.thoughtcrime.securesms.badges.gifts.flow

import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentProcessorAction
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.gateway.GatewaySelectorBottomSheet
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.conversation.preferences.RecipientPreference
import org.thoughtcrime.securesms.components.settings.models.TextInput
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.math.BigDecimal

/**
 * Allows the user to confirm details about a gift, add a message, and finally make a payment.
 */
class GiftFlowConfirmationFragment :
  DSLSettingsFragment(
    titleId = R.string.GiftFlowConfirmationFragment__confirm_donation,
    layoutId = R.layout.gift_flow_confirmation_fragment
  ),
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback,
  InAppPaymentCheckoutDelegate.Callback {

  companion object {
    private val TAG = Log.tag(GiftFlowConfirmationFragment::class.java)
  }

  private val viewModel: GiftFlowViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private val keyboardPagerViewModel: KeyboardPagerViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private lateinit var inputAwareLayout: InputAwareLayout
  private lateinit var emojiKeyboard: MediaKeyboard

  private val lifecycleDisposable = LifecycleDisposable()
  private lateinit var processingDonationPaymentDialog: AlertDialog
  private lateinit var verifyingRecipientDonationPaymentDialog: AlertDialog
  private lateinit var textInputViewHolder: TextInput.MultilineViewHolder

  private val eventPublisher = PublishSubject.create<TextInput.TextInputEvent>()
  private val debouncer = Debouncer(100L)

  override fun bindAdapter(adapter: MappingAdapter) {
    RecipientPreference.register(adapter)
    GiftRowItem.register(adapter)

    val checkoutDelegate = InAppPaymentCheckoutDelegate(this, this, viewModel.state.filter { it.inAppPaymentId != null }.map { it.inAppPaymentId!! })

    keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)

    processingDonationPaymentDialog = MaterialAlertDialogBuilder(requireContext())
      .setView(R.layout.processing_payment_dialog)
      .setCancelable(false)
      .create()

    verifyingRecipientDonationPaymentDialog = MaterialAlertDialogBuilder(requireContext())
      .setView(R.layout.verifying_recipient_payment_dialog)
      .setCancelable(false)
      .create()

    inputAwareLayout = requireView().findViewById(R.id.input_aware_layout)
    emojiKeyboard = requireView().findViewById(R.id.emoji_drawer)

    emojiKeyboard.setFragmentManager(childFragmentManager)

    setFragmentResultListener(GatewaySelectorBottomSheet.REQUEST_KEY) { _, bundle ->
      if (bundle.containsKey(GatewaySelectorBottomSheet.FAILURE_KEY)) {
        showSepaEuroMaximumDialog(FiatMoney(bundle.getSerializable(GatewaySelectorBottomSheet.SEPA_EURO_MAX) as BigDecimal, CurrencyUtil.EURO))
      } else {
        val inAppPayment: InAppPaymentTable.InAppPayment = bundle.getParcelableCompat(GatewaySelectorBottomSheet.REQUEST_KEY, InAppPaymentTable.InAppPayment::class.java)!!
        checkoutDelegate.handleGatewaySelectionResponse(inAppPayment)
      }
    }

    val continueButton = requireView().findViewById<MaterialButton>(R.id.continue_button)
    continueButton.setOnClickListener {
      lifecycleDisposable += viewModel.insertInAppPayment(requireContext()).subscribe { inAppPayment ->
        findNavController().safeNavigate(
          GiftFlowConfirmationFragmentDirections.actionGiftFlowConfirmationFragmentToGatewaySelectorBottomSheet(
            inAppPayment
          )
        )
      }
    }

    val textInput = requireView().findViewById<FrameLayout>(R.id.text_input)
    val emojiToggle = textInput.findViewById<ImageView>(R.id.emoji_toggle)
    val amountView = requireView().findViewById<TextView>(R.id.amount)
    textInputViewHolder = TextInput.MultilineViewHolder(textInput, eventPublisher)
    textInputViewHolder.onAttachedToWindow()

    inputAwareLayout.addOnKeyboardShownListener {
      if (emojiKeyboard.isEmojiSearchMode) {
        return@addOnKeyboardShownListener
      }

      inputAwareLayout.hideAttachedInput(true)
      emojiToggle.setImageResource(R.drawable.ic_emoji_smiley_24)
    }

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (inputAwareLayout.isInputOpen) {
            inputAwareLayout.hideAttachedInput(true)
            emojiToggle.setImageResource(R.drawable.ic_emoji_smiley_24)
          } else {
            findNavController().popBackStack()
          }
        }
      }
    )

    textInputViewHolder.bind(
      TextInput.MultilineModel(
        text = viewModel.snapshot.additionalMessage,
        hint = DSLSettingsText.from(R.string.GiftFlowConfirmationFragment__add_a_message),
        onTextChanged = {
          viewModel.setAdditionalMessage(it)
        },
        onEmojiToggleClicked = {
          if ((inputAwareLayout.isKeyboardOpen && !emojiKeyboard.isEmojiSearchMode) || (!inputAwareLayout.isKeyboardOpen && !inputAwareLayout.isInputOpen)) {
            inputAwareLayout.show(it, emojiKeyboard)
            emojiToggle.setImageResource(R.drawable.ic_keyboard_24)
          } else {
            inputAwareLayout.showSoftkey(it)
            emojiToggle.setImageResource(R.drawable.ic_emoji_smiley_24)
          }
        }
      )
    )

    lifecycleDisposable += viewModel.state.observeOn(AndroidSchedulers.mainThread()).subscribe { state ->
      adapter.submitList(getConfiguration(state).toMappingModelList())

      if (state.stage == GiftFlowState.Stage.RECIPIENT_VERIFICATION) {
        debouncer.publish { verifyingRecipientDonationPaymentDialog.show() }
      } else {
        debouncer.clear()
        verifyingRecipientDonationPaymentDialog.dismiss()
      }

      if (state.stage == GiftFlowState.Stage.PAYMENT_PIPELINE) {
        processingDonationPaymentDialog.show()
      } else {
        processingDonationPaymentDialog.dismiss()
      }

      amountView.text = FiatMoneyUtil.format(resources, state.giftPrices[state.currency]!!, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    textInputViewHolder.onDetachedFromWindow()
    processingDonationPaymentDialog.dismiss()
    debouncer.clear()
    verifyingRecipientDonationPaymentDialog.dismiss()
  }

  private fun getConfiguration(giftFlowState: GiftFlowState): DSLConfiguration {
    return configure {
      if (giftFlowState.giftBadge != null) {
        giftFlowState.giftPrices[giftFlowState.currency]?.let {
          customPref(
            GiftRowItem.Model(
              giftBadge = giftFlowState.giftBadge,
              price = it
            )
          )
        }
      }

      sectionHeaderPref(R.string.GiftFlowConfirmationFragment__send_to)

      customPref(
        RecipientPreference.Model(
          recipient = giftFlowState.recipient!!
        )
      )

      textPref(
        summary = DSLSettingsText.from(R.string.GiftFlowConfirmationFragment__the_recipient_will_be_notified)
      )
    }
  }

  override fun onToolbarNavigationClicked() {
    findNavController().popBackStack()
  }

  override fun openEmojiSearch() {
    emojiKeyboard.onOpenEmojiSearch()
  }

  override fun closeEmojiSearch() {
    emojiKeyboard.onCloseEmojiSearch()
  }

  override fun onEmojiSelected(emoji: String?) {
    if (emoji?.isNotEmpty() == true) {
      eventPublisher.onNext(TextInput.TextInputEvent.OnEmojiEvent(emoji))
    }
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    if (keyEvent != null) {
      eventPublisher.onNext(TextInput.TextInputEvent.OnKeyEvent(keyEvent))
    }
  }

  private fun showSepaEuroMaximumDialog(sepaEuroMaximum: FiatMoney) {
    val max = FiatMoneyUtil.format(resources, sepaEuroMaximum, FiatMoneyUtil.formatOptions().trimZerosAfterDecimal())
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.DonateToSignal__donation_amount_too_high)
      .setMessage(getString(R.string.DonateToSignalFragment__you_can_send_up_to_s_via_bank_transfer, max))
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  override fun navigateToStripePaymentInProgress(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      GiftFlowConfirmationFragmentDirections.actionGiftFlowConfirmationFragmentToStripePaymentInProgressFragment(
        InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
        inAppPayment,
        inAppPayment.type
      )
    )
  }

  override fun navigateToPayPalPaymentInProgress(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      GiftFlowConfirmationFragmentDirections.actionGiftFlowConfirmationFragmentToPaypalPaymentInProgressFragment(
        InAppPaymentProcessorAction.PROCESS_NEW_IN_APP_PAYMENT,
        inAppPayment,
        inAppPayment.type
      )
    )
  }

  override fun navigateToCreditCardForm(inAppPayment: InAppPaymentTable.InAppPayment) {
    findNavController().safeNavigate(
      GiftFlowConfirmationFragmentDirections.actionGiftFlowConfirmationFragmentToCreditCardFragment(inAppPayment)
    )
  }

  override fun navigateToIdealDetailsFragment(inAppPayment: InAppPaymentTable.InAppPayment) = error("iDEAL transfer isn't supported for gifts.")

  override fun navigateToBankTransferMandate(inAppPayment: InAppPaymentTable.InAppPayment) = error("Bank transfer isn't supported for gifts.")

  override fun onPaymentComplete(inAppPayment: InAppPaymentTable.InAppPayment) {
    val mainActivityIntent = MainActivity.clearTop(requireContext())

    lifecycleDisposable += ConversationIntents
      .createBuilder(requireContext(), viewModel.snapshot.recipient!!.id, -1L)
      .subscribe { conversationIntent ->
        requireActivity().startActivities(
          arrayOf(mainActivityIntent, conversationIntent.withGiftBadge(viewModel.snapshot.giftBadge!!).build())
        )
      }
  }

  override fun onSubscriptionCancelled(inAppPaymentType: InAppPaymentType) = error("Not supported for gifts")

  override fun onProcessorActionProcessed() {
    // TODO [alex] -- what do?
  }

  override fun onUserLaunchedAnExternalApplication() = error("Not supported for gifts.")

  override fun navigateToDonationPending(inAppPayment: InAppPaymentTable.InAppPayment) = error("Not supported for gifts")
}
