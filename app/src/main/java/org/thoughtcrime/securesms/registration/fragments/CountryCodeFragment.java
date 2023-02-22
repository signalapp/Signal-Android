package org.thoughtcrime.securesms.registration.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.registration.util.RegistrationNumberInputController;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.Objects;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static pigeon.extensions.BuildExtensionsKt.isSignalVersion;
import static pigeon.extensions.KotilinExtensionsKt.onFocusTextChangeListener;

public final class CountryCodeFragment extends LoggingFragment implements RegistrationNumberInputController.Callbacks {

  private static final String NUMBER_COUNTRY_SELECT = "number_country";

  private TextInputLayout                countryCode;
  private CircularProgressMaterialButton next;
  private RegistrationViewModel viewModel;
  private TextView              verifyHeader;

  private final LifecycleDisposable disposables = new LifecycleDisposable();

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_country_code, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    verifyHeader = view.findViewById(R.id.verify_header);
    countryCode = view.findViewById(R.id.country_code);
    next       = view.findViewById(R.id.next_button);

    setDebugLogSubmitMultiTapView(verifyHeader);

    onFocusTextChangeListener(verifyHeader);
    onFocusTextChangeListener(countryCode);

    RegistrationNumberInputController controller = new RegistrationNumberInputController(requireContext(),
                                                                                         this,
                                                                                         new EditText(getContext()),
                                                                                         countryCode);
    next.setOnClickListener(v -> handleRegister(requireContext(), v));

    if (!isSignalVersion()) {
      CountryPickerFragmentArgs arguments = new CountryPickerFragmentArgs.Builder().setResultKey(NUMBER_COUNTRY_SELECT).build();

      countryCode.setOnClickListener(v -> SafeNavigation.safeNavigate(
          Navigation.findNavController(v), R.id.action_pickCountry, arguments.toBundle()
      ));

      verifyHeader.setOnClickListener(v -> SafeNavigation.safeNavigate(
          Navigation.findNavController(v), R.id.action_pickCountry, arguments.toBundle()
      ));

      getParentFragmentManager().setFragmentResultListener(
          NUMBER_COUNTRY_SELECT, this, (requestKey, result) -> {
            int resultCode = result.getInt(CountryPickerFragment.KEY_COUNTRY_CODE);
            String resultCountryName = result.getString(CountryPickerFragment.KEY_COUNTRY);

            viewModel.onCountrySelected(resultCountryName, resultCode);
            controller.setNumberAndCountryCode(viewModel.getNumber());
          }
      );
    }

    disposables.bindTo(getViewLifecycleOwner().getLifecycle());
    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

    controller.prepopulateCountryCode();
    controller.setNumberAndCountryCode(viewModel.getNumber());
  }
  private void handleRegister(@NonNull Context context, @NonNull View view) {
    if (TextUtils.isEmpty(countryCode.getEditText().getText())) {
      showErrorDialog(context, getString(R.string.RegistrationActivity_you_must_specify_your_country_code));
      return;
    }
    SafeNavigation.safeNavigate(Navigation.findNavController(view), CountryCodeFragmentDirections.actionCountryCodeFragmentToEnterPhoneNumberFragment());
  }


  public void showErrorDialog(Context context, String msg) {
    new MaterialAlertDialogBuilder(context).setMessage(msg).setPositiveButton(R.string.ok, null).show();
  }

  @Override
  public void setCountry(int countryCode) {

  }


  @Override public void onNumberFocused() {

  }

  @Override public void onNumberInputDone(@NonNull View view) {

  }

  @Override public void setNationalNumber(@NonNull String number) {

  }
}