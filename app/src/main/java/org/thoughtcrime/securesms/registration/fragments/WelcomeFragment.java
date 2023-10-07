package org.thoughtcrime.securesms.registration.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.ActivityNavigator;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

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
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton;

import java.util.Optional;

import static org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView;

public final class WelcomeFragment extends LoggingFragment {

  private static final String TAG = Log.tag(WelcomeFragment.class);

  private CircularProgressMaterialButton continueButton;
  private RegistrationViewModel          viewModel;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_registration_welcome, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    viewModel = new ViewModelProvider(requireActivity()).get(RegistrationViewModel.class);

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

      initializeNumber(requireContext(), viewModel);

      Log.i(TAG, "Skipping restore because this is a reregistration.");
      viewModel.setWelcomeSkippedOnRestore();
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this),
                                  WelcomeFragmentDirections.actionSkipRestore());
    } else {

      setDebugLogSubmitMultiTapView(view.findViewById(R.id.image));
      setDebugLogSubmitMultiTapView(view.findViewById(R.id.title));

      continueButton = view.findViewById(R.id.welcome_continue_button);
      continueButton.setOnClickListener(v -> onContinueClicked());

      Button restoreFromBackup = view.findViewById(R.id.welcome_transfer_or_restore);
      restoreFromBackup.setOnClickListener(v -> onRestoreFromBackupClicked());

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

  private void onContinueClicked() {
    if (Permissions.isRuntimePermissionsRequired()) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this),
                                  WelcomeFragmentDirections.actionWelcomeFragmentToGrantPermissionsFragment(GrantPermissionsFragment.WelcomeAction.CONTINUE));
    } else {
      gatherInformationAndContinue(
          this,
          viewModel,
          () -> continueButton.setSpinning(),
          () -> continueButton.cancelSpinning(),
          WelcomeFragmentDirections.actionSkipRestore(),
          WelcomeFragmentDirections.actionRestore()
      );
    }
  }

  private void onRestoreFromBackupClicked() {
    if (Permissions.isRuntimePermissionsRequired()) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this),
                                  WelcomeFragmentDirections.actionWelcomeFragmentToGrantPermissionsFragment(GrantPermissionsFragment.WelcomeAction.RESTORE_BACKUP));
    } else {
      gatherInformationAndChooseBackup(this, viewModel, WelcomeFragmentDirections.actionTransferOrRestore());
    }
  }

  static void continueClicked(@NonNull Fragment fragment,
                              @NonNull RegistrationViewModel viewModel,
                              @NonNull Runnable onSearchForBackupStarted,
                              @NonNull Runnable onSearchForBackupFinished,
                              @NonNull NavDirections actionSkipRestore,
                              @NonNull NavDirections actionRestore)
  {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(fragment.requireContext());

    Permissions.with(fragment)
               .request(WelcomePermissions.getWelcomePermissions(isUserSelectionRequired))
               .ifNecessary()
               .onAnyResult(() -> gatherInformationAndContinue(fragment,
                                                               viewModel,
                                                               onSearchForBackupStarted,
                                                               onSearchForBackupFinished,
                                                               actionSkipRestore,
                                                               actionRestore))
               .execute();
  }

  static void restoreFromBackupClicked(@NonNull Fragment fragment,
                                       @NonNull RegistrationViewModel viewModel,
                                       @NonNull NavDirections actionTransferOrRestore)
  {
    boolean isUserSelectionRequired = BackupUtil.isUserSelectionRequired(fragment.requireContext());

    Permissions.with(fragment)
               .request(WelcomePermissions.getWelcomePermissions(isUserSelectionRequired))
               .ifNecessary()
               .onAnyResult(() -> gatherInformationAndChooseBackup(fragment, viewModel, actionTransferOrRestore))
               .execute();
  }

  static void gatherInformationAndContinue(
      @NonNull Fragment fragment,
      @NonNull RegistrationViewModel viewModel,
      @NonNull Runnable onSearchForBackupStarted,
      @NonNull Runnable onSearchForBackupFinished,
      @NonNull NavDirections actionSkipRestore,
      @NonNull NavDirections actionRestore
  ) {
    onSearchForBackupStarted.run();

    RestoreBackupFragment.searchForBackup(backup -> {
      Context context = fragment.getContext();
      if (context == null) {
        Log.i(TAG, "No context on fragment, must have navigated away.");
        return;
      }

      TextSecurePreferences.setHasSeenWelcomeScreen(fragment.requireContext(), true);

      initializeNumber(fragment.requireContext(), viewModel);

      onSearchForBackupFinished.run();

      if (backup == null) {
        Log.i(TAG, "Skipping backup. No backup found, or no permission to look.");
        SafeNavigation.safeNavigate(NavHostFragment.findNavController(fragment),
                                    actionSkipRestore);
      } else {
        SafeNavigation.safeNavigate(NavHostFragment.findNavController(fragment),
                                    actionRestore);
      }
    });
  }

  static void gatherInformationAndChooseBackup(@NonNull Fragment fragment,
                                               @NonNull RegistrationViewModel viewModel,
                                               @NonNull NavDirections actionTransferOrRestore) {
    TextSecurePreferences.setHasSeenWelcomeScreen(fragment.requireContext(), true);

    initializeNumber(fragment.requireContext(), viewModel);

    SafeNavigation.safeNavigate(NavHostFragment.findNavController(fragment),
                                actionTransferOrRestore);
  }

  @SuppressLint("MissingPermission")
  private static void initializeNumber(@NonNull Context context, @NonNull RegistrationViewModel viewModel) {
    Optional<Phonenumber.PhoneNumber> localNumber = Optional.empty();

    if (Permissions.hasAll(context, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)) {
      localNumber = Util.getDeviceNumber(context);
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
      Optional<String> simCountryIso = Util.getSimCountryIso(context);

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
}
