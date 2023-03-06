package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.LabeledEditText
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getViewModel
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberViewModel.ContinueStatus
import org.thoughtcrime.securesms.databinding.FragmentChangeNumberEnterPhoneNumberBinding
import org.thoughtcrime.securesms.registration.fragments.CountryPickerFragment
import org.thoughtcrime.securesms.registration.fragments.CountryPickerFragmentArgs
import org.thoughtcrime.securesms.registration.util.ChangeNumberInputController
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private const val OLD_NUMBER_COUNTRY_SELECT = "old_number_country"
private const val NEW_NUMBER_COUNTRY_SELECT = "new_number_country"

class ChangeNumberEnterPhoneNumberFragment : LoggingFragment(R.layout.fragment_change_number_enter_phone_number) {

  private var binding: FragmentChangeNumberEnterPhoneNumberBinding? = null

  private val scrollView: ScrollView
    get() = binding!!.changeNumberEnterPhoneNumberScroll

  private val oldNumberCountrySpinner: Spinner
    get() = binding!!.changeNumberEnterPhoneNumberOldNumberSpinner
  private val oldNumberCountryCode: LabeledEditText
    get() = binding!!.changeNumberEnterPhoneNumberOldNumberCountryCode
  private val oldNumber: LabeledEditText
    get() = binding!!.changeNumberEnterPhoneNumberOldNumberNumber

  private val newNumberCountrySpinner: Spinner
    get() = binding!!.changeNumberEnterPhoneNumberNewNumberSpinner
  private val newNumberCountryCode: LabeledEditText
    get() = binding!!.changeNumberEnterPhoneNumberNewNumberCountryCode
  private val newNumber: LabeledEditText
    get() = binding!!.changeNumberEnterPhoneNumberNewNumberNumber

  private lateinit var viewModel: ChangeNumberViewModel

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding = FragmentChangeNumberEnterPhoneNumberBinding.bind(view)

    viewModel = getViewModel(this)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberEnterPhoneNumberFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    view.findViewById<View>(R.id.change_number_enter_phone_number_continue).setOnClickListener {
      onContinue()
    }

    val oldController = ChangeNumberInputController(
      requireContext(),
      oldNumberCountryCode,
      oldNumber,
      oldNumberCountrySpinner,
      false,
      object : ChangeNumberInputController.Callbacks {
        override fun onNumberFocused() {
          scrollView.postDelayed({ scrollView.smoothScrollTo(0, oldNumber.bottom) }, 250)
        }

        override fun onNumberInputNext(view: View) {
          newNumberCountryCode.requestFocus()
        }

        override fun onNumberInputDone(view: View) = Unit

        override fun onPickCountry(view: View) {
          val arguments: CountryPickerFragmentArgs = CountryPickerFragmentArgs.Builder().setResultKey(OLD_NUMBER_COUNTRY_SELECT).build()

          findNavController().safeNavigate(R.id.action_enterPhoneNumberChangeFragment_to_countryPickerFragment, arguments.toBundle())
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
      newNumberCountryCode,
      newNumber,
      newNumberCountrySpinner,
      true,
      object : ChangeNumberInputController.Callbacks {
        override fun onNumberFocused() {
          scrollView.postDelayed({ scrollView.smoothScrollTo(0, newNumber.bottom) }, 250)
        }

        override fun onNumberInputNext(view: View) = Unit

        override fun onNumberInputDone(view: View) {
          onContinue()
        }

        override fun onPickCountry(view: View) {
          val arguments: CountryPickerFragmentArgs = CountryPickerFragmentArgs.Builder().setResultKey(NEW_NUMBER_COUNTRY_SELECT).build()

          findNavController().safeNavigate(R.id.action_enterPhoneNumberChangeFragment_to_countryPickerFragment, arguments.toBundle())
        }

        override fun setNationalNumber(number: String) {
          viewModel.setNewNationalNumber(number)
        }

        override fun setCountry(countryCode: Int) {
          viewModel.setNewCountry(countryCode)
        }
      }
    )

    parentFragmentManager.setFragmentResultListener(OLD_NUMBER_COUNTRY_SELECT, this) { _, bundle ->
      viewModel.setOldCountry(bundle.getInt(CountryPickerFragment.KEY_COUNTRY_CODE), bundle.getString(CountryPickerFragment.KEY_COUNTRY))
    }

    parentFragmentManager.setFragmentResultListener(NEW_NUMBER_COUNTRY_SELECT, this) { _, bundle ->
      viewModel.setNewCountry(bundle.getInt(CountryPickerFragment.KEY_COUNTRY_CODE), bundle.getString(CountryPickerFragment.KEY_COUNTRY))
    }

    viewModel.getLiveOldNumber().observe(viewLifecycleOwner, oldController::updateNumber)
    viewModel.getLiveNewNumber().observe(viewLifecycleOwner, newController::updateNumber)
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  private fun onContinue() {
    if (TextUtils.isEmpty(oldNumberCountryCode.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_old_number_country_code), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(oldNumber.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_old_phone_number), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(newNumberCountryCode.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_new_number_country_code), Toast.LENGTH_LONG).show()
      return
    }

    if (TextUtils.isEmpty(newNumber.text)) {
      Toast.makeText(context, getString(R.string.ChangeNumberEnterPhoneNumberFragment__you_must_specify_your_new_phone_number), Toast.LENGTH_LONG).show()
      return
    }

    when (viewModel.canContinue()) {
      ContinueStatus.CAN_CONTINUE -> findNavController().safeNavigate(R.id.action_enterPhoneNumberChangeFragment_to_changePhoneNumberConfirmFragment)
      ContinueStatus.INVALID_NUMBER -> {
        Dialogs.showAlertDialog(
          context,
          getString(R.string.RegistrationActivity_invalid_number),
          String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid), viewModel.number.e164Number)
        )
      }
      ContinueStatus.OLD_NUMBER_DOESNT_MATCH -> {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.ChangeNumberEnterPhoneNumberFragment__the_phone_number_you_entered_doesnt_match_your_accounts)
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    }
  }
}
