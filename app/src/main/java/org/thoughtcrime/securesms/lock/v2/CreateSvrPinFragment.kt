package org.thoughtcrime.securesms.lock.v2

import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.EditText
import androidx.annotation.PluralsRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinViewModel.NavigationEvent
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinViewModel.PinErrorEvent
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class CreateSvrPinFragment : BaseSvrPinFragment<CreateSvrPinViewModel?>() {
  override fun initializeViewStates() {
    val args = CreateSvrPinFragmentArgs.fromBundle(requireArguments())
    if (args.isPinChange) {
      initializeViewStatesForPinChange()
    } else {
      initializeViewStatesForPinCreate()
    }
    label.text = getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits)
    confirm.isEnabled = false
  }

  private fun initializeViewStatesForPinChange() {
    title.setText(R.string.CreateKbsPinFragment__create_a_new_pin)
    description.setText(R.string.CreateKbsPinFragment__you_can_choose_a_new_pin_as_long_as_this_device_is_registered)
    description.setLearnMoreVisible(true)
  }

  private fun initializeViewStatesForPinCreate() {
    title.setText(R.string.CreateKbsPinFragment__create_your_pin)
    description.setText(R.string.CreateKbsPinFragment__pins_can_help_you_restore_your_account)
    description.setLearnMoreVisible(true)
  }

  override fun initializeViewModel(): CreateSvrPinViewModel {
    val viewModel = ViewModelProvider(this)[CreateSvrPinViewModel::class.java]
    val args = CreateSvrPinFragmentArgs.fromBundle(requireArguments())
    viewModel.navigationEvents.observe(viewLifecycleOwner) { e: NavigationEvent -> onConfirmPin(e.userEntry, e.keyboard, args.isPinChange) }
    viewModel.errorEvents.observe(viewLifecycleOwner) { e: PinErrorEvent ->
      if (e == PinErrorEvent.WEAK_PIN) {
        label.text = SpanUtil.color(
          ContextCompat.getColor(requireContext(), R.color.red_500),
          getString(R.string.CreateKbsPinFragment__choose_a_stronger_pin)
        )
        shake(input) { input.text.clear() }
      } else {
        throw AssertionError("Unexpected PIN error!")
      }
    }
    viewModel.keyboard.observe(viewLifecycleOwner) { k: PinKeyboardType ->
      label.text = getLabelText(k)
      input.text.clear()
    }
    return viewModel
  }

  private fun onConfirmPin(userEntry: SvrPin, keyboard: PinKeyboardType, isPinChange: Boolean) {
    val action = CreateSvrPinFragmentDirections.actionConfirmPin()
    action.userEntry = userEntry
    action.keyboard = keyboard
    action.isPinChange = isPinChange
    findNavController(requireView()).safeNavigate(action)
  }

  private fun getLabelText(keyboard: PinKeyboardType): String {
    return if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_characters)
    } else {
      getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits)
    }
  }

  private fun getPinLengthRestrictionText(@PluralsRes plurals: Int): String {
    return resources.getQuantityString(plurals, SvrConstants.MINIMUM_PIN_LENGTH, SvrConstants.MINIMUM_PIN_LENGTH)
  }

  companion object {
    private fun shake(view: EditText, afterwards: Runnable) {
      val shake = TranslateAnimation(0F, 30F, 0F, 0F)
      shake.duration = 50
      shake.repeatCount = 7
      shake.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {}
        override fun onAnimationEnd(animation: Animation) {
          afterwards.run()
        }

        override fun onAnimationRepeat(animation: Animation) {}
      })
      view.startAnimation(shake)
    }
  }
}
