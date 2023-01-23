package org.thoughtcrime.securesms.lock.v2

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import androidx.autofill.HintConstants
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.lock.v2.ConfirmKbsPinViewModel.SaveAnimation
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.registration.RegistrationUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.SpanUtil

internal class ConfirmKbsPinFragment : BaseKbsPinFragment<ConfirmKbsPinViewModel>() {

  override fun initializeViewStates() {
    val args = ConfirmKbsPinFragmentArgs.fromBundle(requireArguments())
    if (args.isPinChange) {
      initializeViewStatesForPinChange()
    } else {
      initializeViewStatesForPinCreate()
    }
    ViewCompat.setAutofillHints(input, HintConstants.AUTOFILL_HINT_NEW_PASSWORD)
  }

  override fun initializeViewModel(): ConfirmKbsPinViewModel {
    val args = ConfirmKbsPinFragmentArgs.fromBundle(requireArguments())
    val userEntry = args.userEntry!!
    val keyboard = args.keyboard
    val repository = ConfirmKbsPinRepository()
    val factory = ConfirmKbsPinViewModel.Factory(userEntry, keyboard, repository)
    val viewModel = ViewModelProvider(this, factory)[ConfirmKbsPinViewModel::class.java]
    viewModel.label.observe(viewLifecycleOwner) { label: ConfirmKbsPinViewModel.LabelState -> updateLabel(label) }
    viewModel.saveAnimation.observe(viewLifecycleOwner) { animation: SaveAnimation -> updateSaveAnimation(animation) }
    return viewModel
  }

  private fun initializeViewStatesForPinCreate() {
    title.setText(R.string.ConfirmKbsPinFragment__confirm_your_pin)
    description.setText(R.string.ConfirmKbsPinFragment__re_enter_the_pin_you_just_created)
    keyboardToggle.visibility = View.INVISIBLE
    description.setLearnMoreVisible(false)
    label.text = ""
    confirm.isEnabled = true
  }

  private fun initializeViewStatesForPinChange() {
    title.setText(R.string.ConfirmKbsPinFragment__confirm_your_pin)
    description.setText(R.string.ConfirmKbsPinFragment__re_enter_the_pin_you_just_created)
    description.setLearnMoreVisible(false)
    keyboardToggle.visibility = View.INVISIBLE
    label.text = ""
    confirm.isEnabled = true
  }

  private fun updateLabel(labelState: ConfirmKbsPinViewModel.LabelState) {
    when (labelState) {
      ConfirmKbsPinViewModel.LabelState.EMPTY -> label.text = ""
      ConfirmKbsPinViewModel.LabelState.CREATING_PIN -> {
        label.setText(R.string.ConfirmKbsPinFragment__creating_pin)
        input.isEnabled = false
      }
      ConfirmKbsPinViewModel.LabelState.RE_ENTER_PIN -> label.setText(R.string.ConfirmKbsPinFragment__re_enter_your_pin)
      ConfirmKbsPinViewModel.LabelState.PIN_DOES_NOT_MATCH -> {
        label.text = SpanUtil.color(
          ContextCompat.getColor(requireContext(), R.color.red_500),
          getString(R.string.ConfirmKbsPinFragment__pins_dont_match)
        )
        input.text.clear()
      }
    }
  }

  private fun updateSaveAnimation(animation: SaveAnimation) {
    updateInputVisibility(animation)
    when (animation) {
      SaveAnimation.NONE -> confirm.cancelSpinning()
      SaveAnimation.LOADING -> confirm.setSpinning()
      SaveAnimation.SUCCESS -> {
        confirm.cancelSpinning()
        requireActivity().setResult(Activity.RESULT_OK)
        closeNavGraphBranch()
        RegistrationUtil.maybeMarkRegistrationComplete(requireContext())
        StorageSyncHelper.scheduleSyncForDataChange()
      }
      SaveAnimation.FAILURE -> {
        confirm.cancelSpinning()
        RegistrationUtil.maybeMarkRegistrationComplete(requireContext())
        displayFailedDialog()
      }
    }
  }

  private fun updateInputVisibility(saveAnimation: SaveAnimation) {
    if (saveAnimation == SaveAnimation.NONE) {
      input.visibility = View.VISIBLE
    } else {
      input.visibility = View.GONE
    }
  }

  private fun displayFailedDialog() {
    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ConfirmKbsPinFragment__pin_creation_failed)
      .setMessage(R.string.ConfirmKbsPinFragment__your_pin_was_not_saved)
      .setCancelable(false)
      .setPositiveButton(R.string.ok) { d: DialogInterface, w: Int ->
        d.dismiss()
        markMegaphoneSeenIfNecessary()
        requireActivity().setResult(Activity.RESULT_CANCELED)
        closeNavGraphBranch()
      }
      .show()
  }

  private fun markMegaphoneSeenIfNecessary() {
    ApplicationDependencies.getMegaphoneRepository().markSeen(Megaphones.Event.PINS_FOR_ALL)
  }
}
