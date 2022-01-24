package org.thoughtcrime.securesms.payments.backup.entry;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.Mnemonic;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

public class PaymentsRecoveryEntryFragment extends Fragment {

  public PaymentsRecoveryEntryFragment() {
    super(R.layout.payments_recovery_entry_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar                        toolbar   = view.findViewById(R.id.payments_recovery_entry_fragment_toolbar);
    TextView                       message   = view.findViewById(R.id.payments_recovery_entry_fragment_message);
    TextInputLayout                wrapper   = view.findViewById(R.id.payments_recovery_entry_fragment_word_wrapper);
    MaterialAutoCompleteTextView   word      = view.findViewById(R.id.payments_recovery_entry_fragment_word);
    View                           next      = view.findViewById(R.id.payments_recovery_entry_fragment_next);
    PaymentsRecoveryEntryViewModel viewModel = ViewModelProviders.of(this).get(PaymentsRecoveryEntryViewModel.class);

    toolbar.setNavigationOnClickListener(t -> Navigation.findNavController(view).popBackStack(R.id.paymentsHome, false));

    viewModel.getState().observe(getViewLifecycleOwner(), state -> {
      message.setText(getString(R.string.PaymentsRecoveryEntryFragment__enter_word_d, state.getWordIndex() + 1));
      word.setHint(getString(R.string.PaymentsRecoveryEntryFragment__word_d, state.getWordIndex() + 1));
      wrapper.setError(state.canMoveToNext() || TextUtils.isEmpty(state.getCurrentEntry()) ? null : getString(R.string.PaymentsRecoveryEntryFragment__invalid_word));
      next.setEnabled(state.canMoveToNext());

      String inTextView = word.getText().toString();
      String inState    = Util.firstNonNull(state.getCurrentEntry(), "");

      if (!inTextView.equals(inState)) {
        word.setText(inState);
      }
    });

    viewModel.getEvents().observe(getViewLifecycleOwner(), event -> {
      if (event == PaymentsRecoveryEntryViewModel.Events.GO_TO_CONFIRM) {
        SafeNavigation.safeNavigate(Navigation.findNavController(view), PaymentsRecoveryEntryFragmentDirections.actionPaymentsRecoveryEntryToPaymentsRecoveryPhrase(false)
                                                                                                               .setWords(viewModel.getWords()));
      }
    });

    ArrayAdapter<String> wordAdapter = new ArrayAdapter<>(requireContext(), R.layout.support_simple_spinner_dropdown_item, Mnemonic.BIP39_WORDS_ENGLISH);

    word.setAdapter(wordAdapter);
    word.addTextChangedListener(new AfterTextChanged(e -> viewModel.onWordChanged(e.toString())));
    next.setOnClickListener(v -> viewModel.onNextClicked());
  }
}
