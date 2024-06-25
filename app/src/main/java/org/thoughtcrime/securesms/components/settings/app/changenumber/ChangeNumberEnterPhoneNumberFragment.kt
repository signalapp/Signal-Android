/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentChangeNumberEnterPhoneNumberBinding
import org.thoughtcrime.securesms.registration.fragments.CountryPickerFragment
import org.thoughtcrime.securesms.registration.util.ChangeNumberInputController
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Screen for the user to enter their old and new phone numbers.
 */
class ChangeNumberEnterPhoneNumberFragment : LoggingFragment(R.layout.fragment_change_number_enter_phone_number) {

  companion object {
    private val TAG: String = Log.tag(ChangeNumberEnterPhoneNumberFragment::class.java)

    private const val OLD_NUMBER_COUNTRY_SELECT = "old_number_country"
    private const val NEW_NUMBER_COUNTRY_SELECT = "new_number_country"
  }

  private val binding: FragmentChangeNumberEnterPhoneNumberBinding by ViewBinderDelegate(FragmentChangeNumberEnterPhoneNumberBinding::bind)
  private val viewModel by activityViewModels<ChangeNumberViewModel>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberEnterPhoneNumberFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    binding.changeNumberEnterPhoneNumberContinue.setOnClickListener {
      onContinue()
    }

    val oldController = ChangeNumberInputController(
      requireContext(),
      binding.changeNumberEnterPhoneNumberOldNumberCountryCode,
      binding.changeNumberEnterPhoneNumberOldNumberNumber,
      binding.changeNumberEnterPhoneNumberOldNumberSpinner,
      false,
      object : ChangeNumberInputController.Callbacks {
        override fun onNumberFocused() {
          binding.changeNumberEnterPhoneNumberScroll.postDelayed({ binding.changeNumberEnterPhoneNumberScroll.smoothScrollTo(0, binding.changeNumberEnterPhoneNumberOldNumberNumber.bottom) }, 250)
        }

        override fun onNumberInputNext(view: View) {
          binding.changeNumberEnterPhoneNumberNewNumberCountryCode.requestFocus()
        }

        override fun onNumberInputDone(view: View) = Unit

        override fun onPickCountry(view: View) {
          findNavController().safeNavigate(ChangeNumberEnterPhoneNumberFragmentDirections.actionEnterPhoneNumberChangeFragmentToCountryPickerFragment(OLD_NUMBER_COUNTRY_SELECT))
        }

        override fun setNationalNumber(number: String) {
          viewModel.setOldNationalNumber(number)
        }

        override fun setCountry(countryCode: Int) {
          viewModel.setOldCountry(countryCode)
        }
      }
    )

    val newController = ChangeNumberInputController(
      requireContext(),
      binding.changeNumberEnterPhoneNumberNewNumberCountryCode,
      binding.changeNumberEnterPhoneNumberNewNumberNumber,
      binding.changeNumberEnterPhoneNumberNewNumberSpinner,
      true,
      object : ChangeNumberInputController.Callbacks {
        override fun onNumberFocused() {
          binding.changeNumberEnterPhoneNumberScroll.postDelayed({ binding.changeNumberEnterPhoneNumberScroll.smoothScrollTo(0, binding.changeNumberEnterPhoneNumberNewNumberNumber.bottom) }, 250)
        }

        override fun onNumberInputNext(view: View) = Unit

        override fun onNumberInputDone(view: View) {
          onContinue()
        }

        override fun onPickCountry(view: View) {
          findNavController().safeNavigate(ChangeNumberEnterPhoneNumberFragmentDirections.actionEnterPhoneNumberChangeFragmentToCountryPickerFragment(NEW_NUMBER_COUNTRY_SELECT))
        }

        override fun setNationalNumber(number: String) {
          viewModel.setNewNationalNumber(number)
        }

        override fun setCountry(countryCode: Int) {
          viewModel.setNewCountry(countryCode)
        }
      }
    )

    parentFragmentManager.setFragmentResultListener(OLD_NUMBER_COUNTRY_SELECT, this) { _: String, bundle: Bundle ->
      viewModel.setOldCountry(bundle.getInt(CountryPickerFragment.KEY_COUNTRY_CODE), bundle.getString(CountryPickerFragment.KEY_COUNTRY))
    }

    parentFragmentManager.setFragmentResultListener(NEW_NUMBER_COUNTRY_SELECT, this) { _: String, bundle: Bundle ->
      viewModel.setNewCountry(bundle.getInt(CountryPickerFragment.KEY_COUNTRY_CODE), bundle.getString(CountryPickerFragment.KEY_COUNTRY))
    }

    viewModel.liveOldNumberState.observe(viewLifecycleOwner, oldController::updateNumber)
    viewModel.liveNewNumberState.observe(viewLifecycleOwner, newController::updateNumber)
  }

  private fun onContinue() {
    if (TextUtils.isEmpty(binding.changeNumberEnterPhoneNumberOldNumberCountryCode.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_old_number_country_code), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(binding.changeNumberEnterPhoneNumberOldNumberNumber.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_old_phone_number), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(binding.changeNumberEnterPhoneNumberNewNumberCountryCode.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_new_number_country_code), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(binding.changeNumberEnterPhoneNumberNewNumberNumber.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_new_phone_number), Toast.LENGTH_LONG).show()
      return
    }

    when (viewModel.canContinue()) {
      ChangeNumberViewModel.ContinueStatus.CAN_CONTINUE -> findNavController().safeNavigate(ChangeNumberEnterPhoneNumberFragmentDirections.actionEnterPhoneNumberChangeFragmentToChangePhoneNumberConfirmFragment())
      ChangeNumberViewModel.ContinueStatus.INVALID_NUMBER -> {
        Dialogs.showAlertDialog(
          context,
          getString(R.string.RegistrationActivity_invalid_number),
          String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), viewModel.number.e164Number)
        )
      }
      ChangeNumberViewModel.ContinueStatus.OLD_NUMBER_DOESNT_MATCH -> {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.ChangeNumberEnterPhoneNumberFragment__the_phone_number_you_entered_doesnt_match_your_accounts)
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    }
  }
}
