package org.thoughtcrime.securesms.lock.v2;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;

public class CreateKbsPinFragment extends BaseKbsPinFragment<CreateKbsPinViewModel> {

  private CreateKbsPinFragmentArgs args;

  @Override
  protected void initializeViewStates() {
    args = CreateKbsPinFragmentArgs.fromBundle(requireArguments());

    if (args.getIsNewPin()) {
      initializeViewStatesForNewPin();
    } else {
      initializeViewStatesForPin();
    }

    getLabel().setText(getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits));
    getConfirm().setEnabled(false);
  }

  private void initializeViewStatesForPin() {
    getTitle().setText(R.string.CreateKbsPinFragment__create_your_pin);
    getDescription().setText(R.string.CreateKbsPinFragment__pins_add_an_extra_layer_of_security);

  }

  private void initializeViewStatesForNewPin() {
    getTitle().setText(R.string.CreateKbsPinFragment__create_a_new_pin);
    getDescription().setText(R.string.CreateKbsPinFragment__because_youre_still_logged_in);
  }

  @Override
  protected CreateKbsPinViewModel initializeViewModel() {
    CreateKbsPinViewModel viewModel = ViewModelProviders.of(this).get(CreateKbsPinViewModel.class);

    viewModel.getKeyboard().observe(getViewLifecycleOwner(), k -> getLabel().setText(getLabelText(k)));
    viewModel.getNavigationEvents().observe(getViewLifecycleOwner(), e -> onConfirmPin(e.getUserEntry(), e.getKeyboard()));

    return viewModel;
  }

  private void onConfirmPin(@NonNull KbsPin userEntry, @NonNull PinKeyboardType keyboard) {
    CreateKbsPinFragmentDirections.ActionConfirmPin action = CreateKbsPinFragmentDirections.actionConfirmPin();

    action.setUserEntry(userEntry);
    action.setKeyboard(keyboard);
    action.setIsNewPin(args.getIsNewPin());

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
    return requireContext().getResources().getQuantityString(plurals, KbsConstants.MINIMUM_NEW_PIN_LENGTH, KbsConstants.MINIMUM_NEW_PIN_LENGTH);
  }
}
