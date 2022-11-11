package org.thoughtcrime.securesms.payments.backup;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BiometricDeviceAuthentication;
import org.thoughtcrime.securesms.BiometricDeviceLockContract;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;

import kotlin.Unit;

public class PaymentsRecoveryStartFragment extends Fragment {

  private static final String TAG = Log.tag(PaymentsRecoveryStartFragment.class);

  private ActivityResultLauncher<String> activityResultLauncher;
  private boolean                        finishOnConfirm;

  public PaymentsRecoveryStartFragment() {
    super(R.layout.payments_recovery_start_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar                           toolbar       = view.findViewById(R.id.payments_recovery_start_fragment_toolbar);
    TextView                          title         = view.findViewById(R.id.payments_recovery_start_fragment_title);
    LearnMoreTextView                 message       = view.findViewById(R.id.payments_recovery_start_fragment_message);
    TextView                          startButton   = view.findViewById(R.id.payments_recovery_start_fragment_start);
    TextView                          pasteButton   = view.findViewById(R.id.payments_recovery_start_fragment_paste);
    PaymentsRecoveryStartFragmentArgs args          = PaymentsRecoveryStartFragmentArgs.fromBundle(requireArguments());
    RecoveryPhraseStates              state         = args.getRecoveryPhraseState();
    OnBackPressed                     onBackPressed = new OnBackPressed(state);

    finishOnConfirm = args.getFinishOnConfirm();

    if (args.getIsRestore()) {
      title.setText(R.string.PaymentsRecoveryStartFragment__enter_recovery_phrase);
      message.setText(getString(R.string.PaymentsRecoveryStartFragment__your_recovery_phrase_is_a, PaymentsConstants.MNEMONIC_LENGTH));
      message.setLink(getString(R.string.PaymentsRecoveryStartFragment__learn_more__restore));
      startButton.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PaymentsRecoveryStartFragmentDirections.actionPaymentsRecoveryStartToPaymentsRecoveryEntry()));
      startButton.setText(R.string.PaymentsRecoveryStartFragment__enter_manually);
      pasteButton.setVisibility(View.VISIBLE);
      pasteButton.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsRecoveryStartFragmentDirections.actionPaymentsRecoveryStartToPaymentsRecoveryPaste()));
    } else {
      title.setText(getTitle(state));
      message.setText(getDescription(state));
      message.setLink(getString(R.string.PaymentsRecoveryStartFragment__learn_more__view));
      startButton.setOnClickListener(v -> {
        if (state == RecoveryPhraseStates.FROM_PAYMENTS_MENU_WITH_MNEMONIC_CONFIRMED && ServiceUtil.getKeyguardManager(requireContext()).isKeyguardSecure() && SignalStore.paymentsValues().isPaymentLockEnabled()) {
          BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo
                                                                     .Builder()
                                                                     .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
                                                                     .setTitle(requireContext().getString(R.string.BiometricDeviceAuthentication__signal))
                                                                     .setConfirmationRequired(false)
                                                                     .build();
          BiometricDeviceAuthentication biometricAuth = new BiometricDeviceAuthentication(BiometricManager.from(requireActivity()),
                                                                                          new BiometricPrompt(requireActivity(), new BiometricAuthenticationListener()),
                                                                                          promptInfo);
          biometricAuth.authenticate(requireContext(), true, this::showConfirmDeviceCredentialIntent);
        } else {
          goToRecoveryPhrase();
        }
      });
      startButton.setText(R.string.PaymentsRecoveryStartFragment__start);
      pasteButton.setVisibility(View.GONE);
    }

    message.setLearnMoreVisible(true);
    toolbar.setNavigationOnClickListener(v -> onBackPressed(state));
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressed);
    activityResultLauncher = registerForActivityResult(new BiometricDeviceLockContract(), result -> {
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        goToRecoveryPhrase();
      }
    });
  }

  private Unit showConfirmDeviceCredentialIntent() {
    activityResultLauncher.launch(getString(R.string.BiometricDeviceAuthentication__signal));
    return Unit.INSTANCE;
  }

  private String getTitle(RecoveryPhraseStates state) {
    String title;

    switch (state) {
      case FROM_PAYMENTS_MENU_WITH_MNEMONIC_NOT_CONFIRMED:
      case FROM_INFO_CARD_WITH_MNEMONIC_NOT_CONFIRMED:
      case FIRST_TIME_NON_ZERO_BALANCE_WITH_MNEMONIC_NOT_CONFIRMED:
        title = getString(R.string.PaymentsRecoveryStartFragment__save_recovery_phrase);
        break;
      default:
        title = getString(R.string.PaymentsRecoveryStartFragment__view_recovery_phrase);
    }
    return title;
  }

  private String getDescription(RecoveryPhraseStates state) {
    String description;

    switch (state) {
      case FROM_INFO_CARD_WITH_MNEMONIC_NOT_CONFIRMED:
        description = getString(R.string.PaymentsRecoveryStartFragment__time_to_save);
        break;
      case FIRST_TIME_NON_ZERO_BALANCE_WITH_MNEMONIC_NOT_CONFIRMED:
        description = getString(R.string.PaymentsRecoveryStartFragment__got_balance);
        break;
      default:
        description = getResources().getQuantityString(R.plurals.PaymentsRecoveryStartFragment__your_balance_will_automatically_restore,
                                                       PaymentsConstants.MNEMONIC_LENGTH,
                                                       PaymentsConstants.MNEMONIC_LENGTH);
    }
    return description;
  }

  private void goToRecoveryPhrase() {
    PaymentsRecoveryStartFragmentArgs args = PaymentsRecoveryStartFragmentArgs.fromBundle(requireArguments());
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PaymentsRecoveryStartFragmentDirections.actionPaymentsRecoveryStartToPaymentsRecoveryPhrase(args.getFinishOnConfirm()));
  }

  private void onBackPressed(RecoveryPhraseStates state) {
    if (state == RecoveryPhraseStates.FIRST_TIME_NON_ZERO_BALANCE_WITH_MNEMONIC_NOT_CONFIRMED ||
        state == RecoveryPhraseStates.FROM_INFO_CARD_WITH_MNEMONIC_NOT_CONFIRMED)
    {
      showSkipRecoveryDialog();
    } else {
      goBack();
    }
  }

  private void goBack() {
    if (finishOnConfirm) {
      requireActivity().finish();
    } else {
      Navigation.findNavController(requireView()).popBackStack();
    }
  }

  private void showSkipRecoveryDialog() {
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.PaymentsRecoveryStartFragment__continue_without_saving)
        .setMessage(R.string.PaymentsRecoveryStartFragment__your_recovery_phrase)
        .setPositiveButton(R.string.PaymentsRecoveryStartFragment__skip_recovery_phrase, (d, w) -> goBack())
        .setNegativeButton(R.string.PaymentsRecoveryStartFragment__cancel, null)
        .show();
  }

  private class OnBackPressed extends OnBackPressedCallback {
    RecoveryPhraseStates state;

    public OnBackPressed(RecoveryPhraseStates state) {
      super(true);
      this.state = state;
    }

    @Override
    public void handleOnBackPressed() {
      onBackPressed(state);
    }
  }

  private class BiometricAuthenticationListener extends BiometricPrompt.AuthenticationCallback {

    @Override
    public void onAuthenticationError(int errorCode, @NonNull CharSequence errorString) {
      Log.w(TAG, "Authentication error: " + errorCode);
      onAuthenticationFailed();
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
      Log.i(TAG, "onAuthenticationSucceeded");
      goToRecoveryPhrase();
    }

    @Override
    public void onAuthenticationFailed() {
      Log.w(TAG, "Unable to authenticate payment lock");
    }
  }
}
