package org.thoughtcrime.securesms.delete;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.LabeledEditText;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

import java.util.Optional;


public class DeleteAccountFragment extends Fragment {

  private ArrayAdapter<String>         countrySpinnerAdapter;
  private TextView                     bullets;
  private LabeledEditText              countryCode;
  private LabeledEditText              number;
  private AsYouTypeFormatter           countryFormatter;
  private DeleteAccountViewModel       viewModel;
  private DeleteAccountProgressDialog  deletionProgressDialog;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.delete_account_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Spinner countrySpinner = view.findViewById(R.id.delete_account_fragment_country_spinner);
    View    confirm        = view.findViewById(R.id.delete_account_fragment_delete);

    bullets     = view.findViewById(R.id.delete_account_fragment_bullets);
    countryCode = view.findViewById(R.id.delete_account_fragment_country_code);
    number      = view.findViewById(R.id.delete_account_fragment_number);

    viewModel = new ViewModelProvider(requireActivity(), new DeleteAccountViewModel.Factory(new DeleteAccountRepository())).get(DeleteAccountViewModel.class);
    viewModel.getCountryDisplayName().observe(getViewLifecycleOwner(), this::setCountryDisplay);
    viewModel.getRegionCode().observe(getViewLifecycleOwner(), this::handleRegionUpdated);
    viewModel.getEvents().observe(getViewLifecycleOwner(), this::handleEvent);
    viewModel.getWalletBalance().observe(getViewLifecycleOwner(), this::updateBullets);

    initializeNumberInput();

    countryCode.getInput().addTextChangedListener(new AfterTextChanged(this::afterCountryCodeChanged));
    countryCode.getInput().setImeOptions(EditorInfo.IME_ACTION_NEXT);
    confirm.setOnClickListener(unused -> viewModel.submit());

    initializeSpinner(countrySpinner);
  }

  private void updateBullets(@NonNull Optional<String> formattedBalance) {
    bullets.setText(buildBulletsText(formattedBalance));
  }

  private @NonNull CharSequence buildBulletsText(@NonNull Optional<String> formattedBalance) {
    SpannableStringBuilder builder =  new SpannableStringBuilder().append(SpanUtil.bullet(getString(R.string.DeleteAccountFragment__delete_your_account_info_and_profile_photo),8))
                                                                  .append("\n")
                                                                  .append(SpanUtil.bullet(getString(R.string.DeleteAccountFragment__delete_all_your_messages),8));

    if (formattedBalance.isPresent()) {
      builder.append("\n");
      builder.append(SpanUtil.bullet(getString(R.string.DeleteAccountFragment__delete_s_in_your_payments_account, formattedBalance.get()),8));
    }

    return builder;
  }

  @SuppressLint("ClickableViewAccessibility")
  private void initializeSpinner(@NonNull Spinner countrySpinner) {
    countrySpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
    countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    countrySpinner.setAdapter(countrySpinnerAdapter);
    countrySpinner.setOnTouchListener((view, event) -> {
      if (event.getAction() == MotionEvent.ACTION_UP) {
        pickCountry();
      }
      return true;
    });
    countrySpinner.setOnKeyListener((view, keyCode, event) -> {
      if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
        pickCountry();
        return true;
      }
      return false;
    });
  }

  private void pickCountry() {
    countryCode.clearFocus();
    DeleteAccountCountryPickerFragment.show(requireFragmentManager());
  }

  private void setCountryDisplay(@NonNull String regionDisplayName) {
    countrySpinnerAdapter.clear();
    if (TextUtils.isEmpty(regionDisplayName)) {
      countrySpinnerAdapter.add(requireContext().getString(R.string.RegistrationActivity_select_your_country));
    } else {
      countrySpinnerAdapter.add(regionDisplayName);
    }
  }

  private void handleRegionUpdated(@Nullable String regionCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();

    countryFormatter = regionCode != null ? util.getAsYouTypeFormatter(regionCode) : null;

    reformatText(number.getText());

    if (!TextUtils.isEmpty(regionCode) && !"ZZ".equals(regionCode)) {
      number.requestFocus();

      int numberLength = number.getText().length();
      number.getInput().setSelection(numberLength, numberLength);

      countryCode.setText(String.valueOf(util.getCountryCodeForRegion(regionCode)));
    }
  }

  private Long reformatText(Editable s) {
    if (countryFormatter == null) {
      return null;
    }

    if (TextUtils.isEmpty(s)) {
      return null;
    }

    countryFormatter.clear();

    String        formattedNumber = null;
    StringBuilder justDigits      = new StringBuilder();

    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isDigit(c)) {
        formattedNumber = countryFormatter.inputDigit(c);
        justDigits.append(c);
      }
    }

    if (formattedNumber != null && !s.toString().equals(formattedNumber)) {
      s.replace(0, s.length(), formattedNumber);
    }

    if (justDigits.length() == 0) {
      return null;
    }

    return Long.parseLong(justDigits.toString());
  }

  private void initializeNumberInput() {
    EditText numberInput    = number.getInput();
    Long     nationalNumber = viewModel.getNationalNumber();

    if (nationalNumber != null) {
      number.setText(String.valueOf(nationalNumber));
    } else {
      number.setText("");
    }

    numberInput.addTextChangedListener(new AfterTextChanged(this::afterNumberChanged));
    numberInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
    numberInput.setOnEditorActionListener((v, actionId, event) -> {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v);
        viewModel.submit();
        return true;
      }
      return false;
    });
  }

  private void afterCountryCodeChanged(@Nullable Editable s) {
    if (TextUtils.isEmpty(s) || !TextUtils.isDigitsOnly(s)) {
      viewModel.onCountrySelected(0);
      return;
    }

    viewModel.onCountrySelected(Integer.parseInt(s.toString()));
  }

  private void afterNumberChanged(@Nullable Editable s) {
    Long number = reformatText(s);

    if (number == null) return;

    viewModel.setNationalNumber(number);
  }

  private void handleEvent(@NonNull DeleteAccountEvent deleteAccountEvent) {
    switch (deleteAccountEvent.getType()) {
      case NO_COUNTRY_CODE:
        Snackbar.make(requireView(), R.string.DeleteAccountFragment__no_country_code, Snackbar.LENGTH_SHORT).show();
        break;
      case NO_NATIONAL_NUMBER:
        Snackbar.make(requireView(), R.string.DeleteAccountFragment__no_number, Snackbar.LENGTH_SHORT).show();
        break;
      case NOT_A_MATCH:
        new MaterialAlertDialogBuilder(requireContext())
                       .setMessage(R.string.DeleteAccountFragment__the_phone_number)
                       .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                       .setCancelable(true)
                       .show();
        break;
      case CONFIRM_DELETION:
        new MaterialAlertDialogBuilder(requireContext())
                       .setTitle(R.string.DeleteAccountFragment__are_you_sure)
                       .setMessage(R.string.DeleteAccountFragment__this_will_delete_your_signal_account)
                       .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                       .setPositiveButton(R.string.DeleteAccountFragment__delete_account, this::handleDeleteAccountConfirmation)
                       .setCancelable(true)
                       .show();
        break;
      case LEAVE_GROUPS_FAILED:
      case PIN_DELETION_FAILED:
      case SERVER_DELETION_FAILED:
      case CANCEL_SUBSCRIPTION_FAILED:
        dismissDeletionProgressDialog();
        showNetworkDeletionFailedDialog();
        break;
      case LOCAL_DATA_DELETION_FAILED:
        dismissDeletionProgressDialog();
        showLocalDataDeletionFailedDialog();
        break;
      case LEAVE_GROUPS_PROGRESS:
        ensureDeletionProgressDialog();
        deletionProgressDialog.presentLeavingGroups((DeleteAccountEvent.LeaveGroupsProgress) deleteAccountEvent);
        break;
      case LEAVE_GROUPS_FINISHED:
        ensureDeletionProgressDialog();
        deletionProgressDialog.presentDeletingAccount();
        break;
      case CANCELING_SUBSCRIPTION:
        ensureDeletionProgressDialog();
        deletionProgressDialog.presentCancelingSubscription();
        break;
      default:
        throw new IllegalStateException("Unknown error type: " + deleteAccountEvent);
    }
  }

  private void dismissDeletionProgressDialog() {
    if (deletionProgressDialog != null) {
      deletionProgressDialog.dismiss();
      deletionProgressDialog = null;
    }
  }

  private void showNetworkDeletionFailedDialog() {
    new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.DeleteAccountFragment__account_not_deleted)
                                                    .setMessage(R.string.DeleteAccountFragment__there_was_a_problem)
                                                    .setPositiveButton(android.R.string.ok, this::handleDeleteAccountConfirmation)
                                                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                                                    .setCancelable(true)
                                                    .show();
  }

  private void showLocalDataDeletionFailedDialog() {
    new MaterialAlertDialogBuilder(requireContext())
                   .setMessage(R.string.DeleteAccountFragment__failed_to_delete_local_data)
                   .setPositiveButton(R.string.DeleteAccountFragment__launch_app_settings, (dialog, which) -> {
                     Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                     settingsIntent.setData(Uri.fromParts("package", requireActivity().getPackageName(), null));
                     startActivity(settingsIntent);
                   })
                   .setCancelable(false)
                   .show();
  }

  private void handleDeleteAccountConfirmation(DialogInterface dialog, int which) {
    dialog.dismiss();
    ensureDeletionProgressDialog();
    viewModel.deleteAccount();
  }

  private void ensureDeletionProgressDialog() {
    if (deletionProgressDialog == null) {
      deletionProgressDialog = DeleteAccountProgressDialog.show(requireContext());
    }
  }
}
