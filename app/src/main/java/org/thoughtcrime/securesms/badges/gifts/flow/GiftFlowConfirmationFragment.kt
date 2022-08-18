package org.thoughtcrime.securesms.badges.gifts.flow

import android.content.DialogInterface
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsFragment
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationEvent
import org.thoughtcrime.securesms.components.settings.app.subscription.DonationPaymentComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorDialogs
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationErrorSource
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.components.settings.conversation.preferences.RecipientPreference
import org.thoughtcrime.securesms.components.settings.models.TextInput
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.fragments.requireListener

/**
 * Allows the user to confirm details about a gift, add a message, and finally make a payment.
 */
class GiftFlowConfirmationFragment :
  DSLSettingsFragment(
    titleId = R.string.GiftFlowConfirmationFragment__confirm_gift,
    layoutId = R.layout.gift_flow_confirmation_fragment
  ),
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  EmojiSearchFragment.Callback {

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
  private var errorDialog: DialogInterface? = null
  private lateinit var processingDonationPaymentDialog: AlertDialog
  private lateinit var verifyingRecipientDonationPaymentDialog: AlertDialog
  private lateinit var donationPaymentComponent: DonationPaymentComponent
  private lateinit var textInputViewHolder: TextInput.MultilineViewHolder

  private val eventPublisher = PublishSubject.create<TextInput.TextInputEvent>()
  private val debouncer = Debouncer(100L)

  override fun bindAdapter(adapter: MappingAdapter) {
    RecipientPreference.register(adapter)
    GiftRowItem.register(adapter)

    keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)

    donationPaymentComponent = requireListener()

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

    val googlePayButton = requireView().findViewById<GooglePayButton>(R.id.google_pay_button)
    googlePayButton.setOnGooglePayClickListener {
      viewModel.requestTokenFromGooglePay(getString(R.string.preferences__one_time))
    }

    val textInput = requireView().findViewById<FrameLayout>(R.id.text_input)
    val emojiToggle = textInput.findViewById<ImageView>(R.id.emoji_toggle)
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
          if (inputAwareLayout.isKeyboardOpen || (!inputAwareLayout.isKeyboardOpen && !inputAwareLayout.isInputOpen)) {
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
    }

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    lifecycleDisposable += DonationError
      .getErrorsForSource(DonationErrorSource.GIFT)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { donationError ->
        onPaymentError(donationError)
      }

    lifecycleDisposable += viewModel.events.observeOn(AndroidSchedulers.mainThread()).subscribe { donationEvent ->
      when (donationEvent) {
        is DonationEvent.PaymentConfirmationSuccess -> onPaymentConfirmed()
        DonationEvent.RequestTokenSuccess -> Log.i(TAG, "Successfully got request token from Google Pay")
        DonationEvent.SubscriptionCancelled -> Unit
        is DonationEvent.SubscriptionCancellationFailed -> Unit
      }
    }

    lifecycleDisposable += donationPaymentComponent.googlePayResultPublisher.subscribe {
      viewModel.onActivityResult(it.requestCode, it.resultCode, it.data)
    }
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
        summary = DSLSettingsText.from(R.string.GiftFlowConfirmationFragment__your_gift_will_be_sent_in)
      )
    }
  }

  private fun onPaymentConfirmed() {
    val mainActivityIntent = MainActivity.clearTop(requireContext())
    val conversationIntent = ConversationIntents
      .createBuilder(requireContext(), viewModel.snapshot.recipient!!.id, -1L)
      .withGiftBadge(viewModel.snapshot.giftBadge!!)
      .build()

    requireActivity().startActivities(arrayOf(mainActivityIntent, conversationIntent))
  }

  private fun onPaymentError(throwable: Throwable?) {
    Log.w(TAG, "onPaymentError", throwable, true)

    if (errorDialog != null) {
      Log.i(TAG, "Already displaying an error dialog. Skipping.")
      return
    }

    errorDialog = DonationErrorDialogs.show(
      requireContext(), throwable,
      object : DonationErrorDialogs.DialogCallback() {
        override fun onDialogDismissed() {
          requireActivity().finish()
        }
      }
    )
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
}
