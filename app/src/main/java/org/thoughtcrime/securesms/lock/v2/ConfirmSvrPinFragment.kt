package org.thoughtcrime.securesms.lock.v2

import android.app.Activity
import android.content.DialogInterface
import android.view.View
import androidx.autofill.HintConstants
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.lock.v2.ConfirmSvrPinViewModel.SaveAnimation
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.storage.AndroidCredentialRepository

internal class ConfirmSvrPinFragment : BaseSvrPinFragment<ConfirmSvrPinViewModel>() {

  override fun initializeViewStates() {
    val args = ConfirmSvrPinFragmentArgs.fromBundle(requireArguments())
    if (args.isPinChange) {
      initializeViewStatesForPinChange()
    } else {
      initializeViewStatesForPinCreate()
    }

    ViewCompat.setImportantForAutofill(input, View.IMPORTANT_FOR_AUTOFILL_YES)
    ViewCompat.setAutofillHints(input, HintConstants.AUTOFILL_HINT_NEW_PASSWORD)
  }

  override fun initializeViewModel(): ConfirmSvrPinViewModel {
    val args = ConfirmSvrPinFragmentArgs.fromBundle(requireArguments())
    val userEntry = args.userEntry!!
    val keyboard = args.keyboard
    val factory = ConfirmSvrPinViewModel.Factory(userEntry, keyboard)
    val viewModel = ViewModelProvider(this, factory)[ConfirmSvrPinViewModel::class.java]
    viewModel.label.observe(viewLifecycleOwner) { label: ConfirmSvrPinViewModel.LabelState -> updateLabel(label) }
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

  private fun updateLabel(labelState: ConfirmSvrPinViewModel.LabelState) {
    when (labelState) {
      ConfirmSvrPinViewModel.LabelState.EMPTY -> label.text = ""
      ConfirmSvrPinViewModel.LabelState.CREATING_PIN -> {
        label.setText(R.string.ConfirmKbsPinFragment__creating_pin)
        input.isEnabled = false
      }

      ConfirmSvrPinViewModel.LabelState.RE_ENTER_PIN -> label.setText(R.string.ConfirmKbsPinFragment__re_enter_your_pin)
      ConfirmSvrPinViewModel.LabelState.PIN_DOES_NOT_MATCH -> {
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
        requireActivity().setResult(Activity.RESULT_OK)
        closeNavGraphBranch()
        RegistrationUtil.maybeMarkRegistrationComplete()
        StorageSyncHelper.scheduleSyncForDataChange()
        showSavePinToPasswordManagerPrompt()
      }

      SaveAnimation.FAILURE -> {
        confirm.cancelSpinning()
        RegistrationUtil.maybeMarkRegistrationComplete()
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
      .setPositiveButton(android.R.string.ok) { d: DialogInterface, w: Int ->
        d.dismiss()
        markMegaphoneSeenIfNecessary()
        requireActivity().setResult(Activity.RESULT_CANCELED)
        closeNavGraphBranch()
      }
      .show()
  }

  private fun markMegaphoneSeenIfNecessary() {
    AppDependencies.megaphoneRepository.markSeen(Megaphones.Event.PINS_FOR_ALL)
  }

  private fun showSavePinToPasswordManagerPrompt() {
    CoroutineScope(Dispatchers.Main).launch {
      AndroidCredentialRepository.saveCredential(
        activityContext = requireActivity(),
        username = getString(R.string.ConfirmKbsPinFragment__pin_password_manager_id),
        password = input.text.toString()
      )
    }
  }
}
