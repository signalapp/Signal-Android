package org.thoughtcrime.securesms.payments.backup;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;

public class PaymentsRecoveryStartFragment extends Fragment {

  private final OnBackPressed onBackPressed = new OnBackPressed();

  public PaymentsRecoveryStartFragment() {
    super(R.layout.payments_recovery_start_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar           toolbar     = view.findViewById(R.id.payments_recovery_start_fragment_toolbar);
    TextView          title       = view.findViewById(R.id.payments_recovery_start_fragment_title);
    LearnMoreTextView message     = view.findViewById(R.id.payments_recovery_start_fragment_message);
    TextView          startButton = view.findViewById(R.id.payments_recovery_start_fragment_start);
    TextView          pasteButton = view.findViewById(R.id.payments_recovery_start_fragment_paste);

    PaymentsRecoveryStartFragmentArgs args = PaymentsRecoveryStartFragmentArgs.fromBundle(requireArguments());

    if (args.getIsRestore()) {
      title.setText(R.string.PaymentsRecoveryStartFragment__enter_recovery_phrase);
      message.setText(getString(R.string.PaymentsRecoveryStartFragment__your_recovery_phrase_is_a, PaymentsConstants.MNEMONIC_LENGTH));
      message.setLink(getString(R.string.PaymentsRecoveryStartFragment__learn_more__restore));
      startButton.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PaymentsRecoveryStartFragmentDirections.actionPaymentsRecoveryStartToPaymentsRecoveryEntry()));
      startButton.setText(R.string.PaymentsRecoveryStartFragment__enter_manually);
      pasteButton.setVisibility(View.VISIBLE);
      pasteButton.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsRecoveryStartFragmentDirections.actionPaymentsRecoveryStartToPaymentsRecoveryPaste()));
    } else {
      title.setText(R.string.PaymentsRecoveryStartFragment__view_recovery_phrase);
      message.setText(getString(R.string.PaymentsRecoveryStartFragment__your_balance_will_automatically_restore, PaymentsConstants.MNEMONIC_LENGTH));
      message.setLink(getString(R.string.PaymentsRecoveryStartFragment__learn_more__view));
      startButton.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PaymentsRecoveryStartFragmentDirections.actionPaymentsRecoveryStartToPaymentsRecoveryPhrase(args.getFinishOnConfirm())));
      startButton.setText(R.string.PaymentsRecoveryStartFragment__start);
      pasteButton.setVisibility(View.GONE);
    }

    toolbar.setNavigationOnClickListener(v -> {
      if (args.getFinishOnConfirm()) {
        requireActivity().finish();
      } else {
        Navigation.findNavController(requireView()).popBackStack();
      }
    });

    if (args.getFinishOnConfirm()) {
      requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressed);
    }

    message.setLearnMoreVisible(true);
  }

  private class OnBackPressed extends OnBackPressedCallback {

    public OnBackPressed() {
      super(true);
    }

    @Override
    public void handleOnBackPressed() {
      requireActivity().finish();
    }
  }
}
