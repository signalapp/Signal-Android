package org.thoughtcrime.securesms.lock.v2;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.autofill.HintConstants;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;

public class CreateKbsPinFragment extends BaseKbsPinFragment<CreateKbsPinViewModel> {

  @Override
  protected void initializeViewStates() {
    CreateKbsPinFragmentArgs args = CreateKbsPinFragmentArgs.fromBundle(requireArguments());

    if (args.getIsPinChange()) {
      initializeViewStatesForPinChange(args.getIsForgotPin());
    } else {
      initializeViewStatesForPinCreate();
    }

    getLabel().setText(getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits));
    getConfirm().setEnabled(false);
    ViewCompat.setAutofillHints(getInput(), HintConstants.AUTOFILL_HINT_NEW_PASSWORD);
  }

  private void initializeViewStatesForPinChange(boolean isForgotPin) {
    getTitle().setText(R.string.CreateKbsPinFragment__create_a_new_pin);

    getDescription().setText(R.string.CreateKbsPinFragment__you_can_choose_a_new_pin_as_long_as_this_device_is_registered);
    getDescription().setLearnMoreVisible(true);
  }

  private void initializeViewStatesForPinCreate() {
    getTitle().setText(R.string.CreateKbsPinFragment__create_your_pin);
    getDescription().setText(R.string.CreateKbsPinFragment__pins_keep_information_stored_with_signal_encrypted);
    getDescription().setLearnMoreVisible(true);
  }

  @Override
  protected CreateKbsPinViewModel initializeViewModel() {
    CreateKbsPinViewModel    viewModel = ViewModelProviders.of(this).get(CreateKbsPinViewModel.class);
    CreateKbsPinFragmentArgs args      = CreateKbsPinFragmentArgs.fromBundle(requireArguments());

    viewModel.getNavigationEvents().observe(getViewLifecycleOwner(), e -> onConfirmPin(e.getUserEntry(), e.getKeyboard(), args.getIsPinChange()));
    viewModel.getKeyboard().observe(getViewLifecycleOwner(), k -> {
      getLabel().setText(getLabelText(k));
      getInput().getText().clear();
    });

    return viewModel;
  }

  private void onConfirmPin(@NonNull KbsPin userEntry, @NonNull PinKeyboardType keyboard, boolean isPinChange) {
    CreateKbsPinFragmentDirections.ActionConfirmPin action = CreateKbsPinFragmentDirections.actionConfirmPin();

    action.setUserEntry(userEntry);
    action.setKeyboard(keyboard);
    action.setIsPinChange(isPinChange);

    Navigation.findNavController(requireView()).navigate(action);
  }

  private String getLabelText(@NonNull PinKeyboardType keyboard) {
    if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      return getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_characters);
    } else {
      return getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits);
    }
  }

  private String getPinLengthRestrictionText(@PluralsRes int plurals) {
    return requireContext().getResources().getQuantityString(plurals, KbsConstants.MINIMUM_PIN_LENGTH, KbsConstants.MINIMUM_PIN_LENGTH);
  }
}
