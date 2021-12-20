package org.thoughtcrime.securesms.registration.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.ActivityNavigator;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.dd.CircularProgressButton;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.greenrobot.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.DeviceToDeviceTransferService;
import org.signal.devicetransfer.TransferStatus;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel;
import org.thoughtcrime.securesms.util.BackupUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.whispersystems.libsignal.util.guava.Optional;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;
import static org.thoughtcrime.securesms.util.CircularProgressButtonUtil.cancelSpinning;
import static org.thoughtcrime.securesms.util.CircularProgressButtonUtil.setSpinning;

public final class WelcomeFragment extends LoggingFragment {

  private static final String TAG = Log.tag(WelcomeFragment.class);

  private static final String[] PERMISSIONS        = { Manifest.permission.WRITE_CONTACTS,
                                                       Manifest.permission.READ_CONTACTS,
                                                       Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                       Manifest.permission.READ_EXTERNAL_STORAGE,
                                                       Manifest.permission.READ_PHONE_STATE };
  @RequiresApi(26)
  private static final String[] PERMISSIONS_API_26 = { Manifest.permission.WRITE_CONTACTS,
                                                       Manifest.permission.READ_CONTACTS,
                                                       Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                       Manifest.permission.READ_EXTERNAL_STORAGE,
                                                       Manifest.permission.READ_PHONE_STATE,
                                                       Manifest.permission.READ_PHONE_NUMBERS };
  @RequiresApi(26)
  private static final String[] PERMISSIONS_API_29 = { Manifest.permission.WRITE_CONTACTS,
                                                       Manifest.permission.READ_CONTACTS,
                                                       Manifest.permission.READ_PHONE_STATE,
                                                       Manifest.permission.READ_PHONE_NUMBERS };

  private static final @StringRes int   RATIONALE        = R.string.RegistrationActivity_signal_needs_access_to_your_contacts_and_media_in_order_to_connect_with_friends;
  private static final @StringRes int   RATIONALE_API_29 = R.string.RegistrationActivity_signal_needs_access_to_your_contacts_in_order_to_connect_with_friends;
  private static final            int[] HEADERS          = { R.drawable.ic_contacts_white_48dp, R.drawable.ic_folder_white_48dp };
  private static final            int[] HEADERS_API_29   = { R.drawable.ic_contacts_white_48dp };

  private CircularProgressButton continueButton;
  private RegistrationViewModel  viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_welcome, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    viewModel = ViewModelProviders.of(requireActivity()).get(RegistrationViewModel.class);

    if (viewModel.isReregister()) {
      if (viewModel.hasRestoreFlowBeenShown()) {
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
      viewModel.setWelcomeSkippedOnRestore();
      SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                  WelcomeFragmentDirections.actionSkipRestore());
    } else {

      setDebugLogSubmitMultiTapView(view.findViewById(R.id.image));
      setDebugLogSubmitMultiTapView(view.findViewById(R.id.title));

      continueButton = view.findViewById(R.id.welcome_continue_button);
      continueButton.setOnClickListener(this::continueClicked);

      Button restoreFromBackup = view.findViewById(R.id.welcome_transfer_or_restore);
      restoreFromBackup.setOnClickListener(this::restoreFromBackupClicked);

      TextView welcomeTermsButton = view.findViewById(R.id.welcome_terms_button);
      welcomeTermsButton.setOnClickListener(v -> onTermsClicked());

      if (!canUserSelectBackup()) {
        restoreFromBackup.setText(R.string.registration_activity__transfer_account);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (EventBus.getDefault().getStickyEvent(TransferStatus.class) != null) {
      Log.i(TAG, "Found existing transferStatus, redirect to transfer flow");
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_welcomeFragment_to_deviceTransferSetup);
    } else {
      DeviceToDeviceTransferService.stop(requireContext());
    }
  }

  private void continueClicked(@NonNull View view) {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext());

    Permissions.with(this)
               .request(getContinuePermissions(isUserSelectionRequired))
               .ifNecessary()
               .withRationaleDialog(getString(getContinueRationale(isUserSelectionRequired)), getContinueHeaders(isUserSelectionRequired))
               .onAnyResult(() -> gatherInformationAndContinue(continueButton))
               .execute();
  }

  private void restoreFromBackupClicked(@NonNull View view) {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext());

    Permissions.with(this)
               .request(getContinuePermissions(isUserSelectionRequired))
               .ifNecessary()
               .withRationaleDialog(getString(getContinueRationale(isUserSelectionRequired)), getContinueHeaders(isUserSelectionRequired))
               .onAnyResult(() -> gatherInformationAndChooseBackup(continueButton))
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
        SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                    WelcomeFragmentDirections.actionSkipRestore());
      } else {
        SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                    WelcomeFragmentDirections.actionRestore());
      }
    });
  }

  private void gatherInformationAndChooseBackup(@NonNull View view) {
    TextSecurePreferences.setHasSeenWelcomeScreen(requireContext(), true);

    initializeNumber();

    SafeNavigation.safeNavigate(Navigation.findNavController(view),
                                WelcomeFragmentDirections.actionTransferOrRestore());
  }

  @SuppressLint("MissingPermission")
  private void initializeNumber() {
    Optional<Phonenumber.PhoneNumber> localNumber = Optional.absent();

    if (Permissions.hasAll(requireContext(), Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) {
      localNumber = Util.getDeviceNumber(requireContext());
    } else {
      Log.i(TAG, "No phone permission");
    }

    if (localNumber.isPresent()) {
      Log.i(TAG, "Phone number detected");
      Phonenumber.PhoneNumber phoneNumber    = localNumber.get();
      String                  nationalNumber = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);

      viewModel.onNumberDetected(phoneNumber.getCountryCode(), nationalNumber);
    } else {
      Log.i(TAG, "No number detected");
      Optional<String> simCountryIso = Util.getSimCountryIso(requireContext());

      if (simCountryIso.isPresent() && !TextUtils.isEmpty(simCountryIso.get())) {
        viewModel.onNumberDetected(PhoneNumberUtil.getInstance().getCountryCodeForRegion(simCountryIso.get()), "");
      }
    }
  }

  private void onTermsClicked() {
    CommunicationActions.openBrowserLink(requireContext(), RegistrationConstants.TERMS_AND_CONDITIONS_URL);
  }

  private boolean canUserSelectBackup() {
    return BackupUtil.isUserSelectionRequired(requireContext()) &&
           !viewModel.isReregister() &&
           !SignalStore.settings().isBackupEnabled();
  }

  @SuppressLint("NewApi")
  private static String[] getContinuePermissions(boolean isUserSelectionRequired) {
    if (isUserSelectionRequired) {
      return PERMISSIONS_API_29;
    } else if (Build.VERSION.SDK_INT >= 26) {
      return PERMISSIONS_API_26;
    } else {
      return PERMISSIONS;
    }
  }

  private static @StringRes int getContinueRationale(boolean isUserSelectionRequired) {
    return isUserSelectionRequired ? RATIONALE_API_29 : RATIONALE;
  }

  private static int[] getContinueHeaders(boolean isUserSelectionRequired) {
    return isUserSelectionRequired ? HEADERS_API_29 : HEADERS;
  }
}
