package org.thoughtcrime.securesms.payments.backup.confirm;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputLayout;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

public class PaymentsRecoveryPhraseConfirmFragment extends Fragment {

  /**
   * The minimum number of characters required to show an error mark.
   */
  private static final int      ERROR_THRESHOLD             = 1;
  public static final  String   RECOVERY_PHRASE_CONFIRMED   = "recovery_phrase_confirmed";
  public static final  String   REQUEST_KEY_RECOVERY_PHRASE = "org.thoughtcrime.securesms.payments.backup.confirm.RECOVERY_PHRASE";

  private              Drawable validWordCheckMark;
  private              Drawable invalidWordX;

  public PaymentsRecoveryPhraseConfirmFragment() {
    super(R.layout.payments_recovery_phrase_confirm_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar         toolbar        = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_toolbar);
    EditText        word1          = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_word_1);
    EditText        word2          = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_word_2);
    View            seePhraseAgain = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_see_again);
    View            done           = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_done);
    TextInputLayout wordWrapper1   = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_word1_wrapper);
    TextInputLayout wordWrapper2   = view.findViewById(R.id.payments_recovery_phrase_confirm_fragment_word2_wrapper);

    PaymentsRecoveryPhraseConfirmFragmentArgs args = PaymentsRecoveryPhraseConfirmFragmentArgs.fromBundle(requireArguments());

    validWordCheckMark = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_check_circle_24);
    invalidWordX       = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_circle_x_24);

    DrawableCompat.setTint(validWordCheckMark, ContextCompat.getColor(requireContext(), R.color.signal_accent_green));
    DrawableCompat.setTint(invalidWordX, ContextCompat.getColor(requireContext(), R.color.signal_alert_primary));

    PaymentsRecoveryPhraseConfirmViewModel viewModel = new ViewModelProvider(requireActivity()).get(PaymentsRecoveryPhraseConfirmViewModel.class);

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(requireView()).popBackStack());

    word1.addTextChangedListener(new AfterTextChanged(e -> viewModel.validateWord1(e.toString())));
    word2.addTextChangedListener(new AfterTextChanged(e -> viewModel.validateWord2(e.toString())));
    seePhraseAgain.setOnClickListener(v -> Navigation.findNavController(requireView()).popBackStack());
    done.setOnClickListener(v -> {
      SignalStore.payments().confirmMnemonic(true);
      ViewUtil.hideKeyboard(requireContext(), view);
      Toast.makeText(requireContext(), R.string.PaymentRecoveryPhraseConfirmFragment__recovery_phrase_confirmed, Toast.LENGTH_SHORT).show();

      if (args.getFinishOnConfirm()) {
        requireActivity().setResult(Activity.RESULT_OK);
        requireActivity().finish();
      } else {
        Bundle result = new Bundle();
        result.putBoolean(RECOVERY_PHRASE_CONFIRMED, true);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY_RECOVERY_PHRASE, result);
        Navigation.findNavController(view).popBackStack(R.id.paymentsHome, false);
      }
    });

    viewModel.getViewState().observe(getViewLifecycleOwner(), viewState -> {
      updateValidity(word1, viewState.isWord1Valid());
      updateValidity(word2, viewState.isWord2Valid());
      done.setEnabled(viewState.areAllWordsValid());

      String hint1 = getString(R.string.PaymentRecoveryPhraseConfirmFragment__word_d, viewState.getWord1Index() + 1);
      String hint2 = getString(R.string.PaymentRecoveryPhraseConfirmFragment__word_d, viewState.getWord2Index() + 1);

      wordWrapper1.setHint(hint1);
      wordWrapper2.setHint(hint2);
    });

    viewModel.updateRandomIndices();
  }

  private void updateValidity(TextView word, boolean isValid) {
    if (isValid) {
      setEndDrawable(word, validWordCheckMark);
    } else if (word.getText().length() >= ERROR_THRESHOLD) {
      setEndDrawable(word, invalidWordX);
    } else {
      word.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
    }
  }

  private void setEndDrawable(@NonNull TextView word, @NonNull Drawable invalidWordX) {
    if (word.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
      word.setCompoundDrawablesWithIntrinsicBounds(null, null, invalidWordX, null);
    } else {
      word.setCompoundDrawablesWithIntrinsicBounds(invalidWordX, null, null, null);
    }
  }
}
