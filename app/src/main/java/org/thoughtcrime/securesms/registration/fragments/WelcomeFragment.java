package org.thoughtcrime.securesms.registration.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.ActivityNavigator;
import androidx.navigation.Navigation;

import com.dd.CircularProgressButton;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;

public final class WelcomeFragment extends BaseRegistrationFragment {

  private static final String TAG = Log.tag(WelcomeFragment.class);

  private CircularProgressButton continueButton;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    return inflater.inflate(isReregister() ? R.layout.fragment_registration_blank
                                           : R.layout.fragment_registration_welcome,
                            container,
                            false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    if (isReregister()) {
      RegistrationViewModel model = getModel();

      if (model.hasRestoreFlowBeenShown()) {
        Log.i(TAG, "We've come back to the home fragment on a restore, user must be backing out");
        if (!Navigation.findNavController(view).popBackStack()) {
          FragmentActivity activity = requireActivity();
          activity.finish();
          ActivityNavigator.applyPopAnimationsToPendingTransition(activity);
        }
        return;
      }

      initializeNumber();

      Log.i(TAG, "Skipping restore because this is a reregistration.");
      model.setWelcomeSkippedOnRestore();
      Navigation.findNavController(view)
                .navigate(WelcomeFragmentDirections.actionSkipRestore());

    } else {

      setDebugLogSubmitMultiTapView(view.findViewById(R.id.image));
      setDebugLogSubmitMultiTapView(view.findViewById(R.id.title));

      continueButton = view.findViewById(R.id.welcome_continue_button);
      continueButton.setOnClickListener(this::continueClicked);

      view.findViewById(R.id.welcome_terms_button).setOnClickListener(v -> onTermsClicked());
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  private void continueClicked(@NonNull View view) {
    Permissions.with(this)
               .request(Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends),
                 R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp)
               .onAnyResult(() -> {
                 gatherInformationAndContinue(continueButton);
               })
               .execute();
  }

  private void gatherInformationAndContinue(@NonNull View view) {
    setSpinning(continueButton);

    RestoreBackupFragment.searchForBackup(backup -> {
      Context context = getContext();
      if (context == null) {
        Log.i(TAG, "No context on fragment, must have navigated away.");
        return;
      }

      TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true);

      initializeNumber();

      cancelSpinning(continueButton);

      if (backup == null) {
        Log.i(TAG, "Skipping backup. No backup found, or no permission to look.");
        Navigation.findNavController(view)
                  .navigate(WelcomeFragmentDirections.actionSkipRestore());
      } else {
        Navigation.findNavController(view)
                  .navigate(WelcomeFragmentDirections.actionRestore());
      }
    });
  }

  @SuppressLint("MissingPermission")
  private void initializeNumber() {
    Optional<Phonenumber.PhoneNumber> localNumber = Optional.absent();

    if (Permissions.hasAll(requireContext(), Manifest.permission.READ_PHONE_STATE)) {
      localNumber = Util.getDeviceNumber(requireContext());
    }

    if (localNumber.isPresent()) {
      getModel().onNumberDetected(localNumber.get().getCountryCode(), localNumber.get().getNationalNumber());
    } else {
      Optional<String> simCountryIso = Util.getSimCountryIso(requireContext());

      if (simCountryIso.isPresent() && !TextUtils.isEmpty(simCountryIso.get())) {
        getModel().onNumberDetected(PhoneNumberUtil.getInstance().getCountryCodeForRegion(simCountryIso.get()), 0);
      }
    }
  }

  private void onTermsClicked() {
    CommunicationActions.openBrowserLink(requireContext(), RegistrationConstants.TERMS_AND_CONDITIONS_URL);
  }
}
