package org.thoughtcrime.securesms.payments.backup.phrase;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.PendingIntentFlags;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Mnemonic;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PaymentsRecoveryPhraseFragment extends Fragment {

  private static final int SPAN_COUNT = 2;

  public PaymentsRecoveryPhraseFragment() {
    super(R.layout.payments_recovery_phrase_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar                            toolbar           = view.findViewById(R.id.payments_recovery_phrase_fragment_toolbar);
    RecyclerView                       recyclerView      = view.findViewById(R.id.payments_recovery_phrase_fragment_recycler);
    TextView                           message           = view.findViewById(R.id.payments_recovery_phrase_fragment_message);
    View                               next              = view.findViewById(R.id.payments_recovery_phrase_fragment_next);
    View                               edit              = view.findViewById(R.id.payments_recovery_phrase_fragment_edit);
    View                               copy              = view.findViewById(R.id.payments_recovery_phrase_fragment_copy);
    GridLayoutManager                  gridLayoutManager = new GridLayoutManager(requireContext(), SPAN_COUNT);
    PaymentsRecoveryPhraseFragmentArgs args              = PaymentsRecoveryPhraseFragmentArgs.fromBundle(requireArguments());

    final List<String> words;

    if (args.getWords() != null) {
      words = Arrays.asList(args.getWords());

      setUpForConfirmation(message, next, edit, copy, words);
    } else {
      Mnemonic mnemonic = SignalStore.payments().getPaymentsMnemonic();

      words = mnemonic.getWords();

      setUpForDisplay(message, next, edit, copy, words, args);
    }

    List<MnemonicPart> parts = Stream.of(words)
                                     .mapIndexed(MnemonicPart::new)
                                     .sorted(new MnemonicPartComparator(words.size(), SPAN_COUNT))
                                     .toList();

    MnemonicPartAdapter adapter = new MnemonicPartAdapter();

    recyclerView.setLayoutManager(gridLayoutManager);
    recyclerView.setAdapter(adapter);
    recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);

    toolbar.setNavigationOnClickListener(v -> {
      if (args.getFinishOnConfirm()) {
        requireActivity().finish();
      } else {
        Navigation.findNavController(view).popBackStack(R.id.paymentsHome, false);
      }
    });

    adapter.submitList(parts);
  }

  private void copyWordsToClipboard(List<String> words) {
    ClipboardManager clipboardManager = ServiceUtil.getClipboardManager(requireContext());
    clipboardManager.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), Util.join(words, " ")));

    AlarmManager  alarmManager       = ServiceUtil.getAlarmManager(requireContext());
    Intent        alarmIntent        = new Intent(requireContext(), ClearClipboardAlarmReceiver.class);
    PendingIntent pendingAlarmIntent = PendingIntent.getBroadcast(requireContext(), 0, alarmIntent, PendingIntentFlags.mutable());

    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30), pendingAlarmIntent);
  }

  private void setUpForConfirmation(@NonNull TextView message,
                                    @NonNull View next,
                                    @NonNull View edit,
                                    @NonNull View copy,
                                    @NonNull List<String> words)
  {
    message.setText(R.string.PaymentsRecoveryPhraseFragment__make_sure_youve_entered);
    edit.setVisibility(View.VISIBLE);
    copy.setVisibility(View.GONE);

    PaymentsRecoveryPhraseViewModel viewModel = new ViewModelProvider(this).get(PaymentsRecoveryPhraseViewModel.class);

    next.setOnClickListener(v -> viewModel.onSubmit(words));
    edit.setOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    viewModel.getSubmitResult().observe(getViewLifecycleOwner(), this::onSubmitResult);
  }

  private void setUpForDisplay(@NonNull TextView message,
                               @NonNull View next,
                               @NonNull View edit,
                               @NonNull View copy,
                               @NonNull List<String> words,
                               @NonNull PaymentsRecoveryPhraseFragmentArgs args)
  {
    message.setText(getString(R.string.PaymentsRecoveryPhraseFragment__write_down_the_following_d_words, words.size()));
    next.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), PaymentsRecoveryPhraseFragmentDirections.actionPaymentsRecoveryPhraseToPaymentsRecoveryPhraseConfirm(args.getFinishOnConfirm())));
    edit.setVisibility(View.GONE);
    copy.setVisibility(View.VISIBLE);
    copy.setOnClickListener(v -> confirmCopy(words));
  }

  private void confirmCopy(@NonNull List<String> words) {
    new MaterialAlertDialogBuilder(requireContext())
                   .setTitle(R.string.PaymentsRecoveryPhraseFragment__copy_to_clipboard)
                   .setMessage(R.string.PaymentsRecoveryPhraseFragment__if_you_choose_to_store)
                   .setPositiveButton(R.string.PaymentsRecoveryPhraseFragment__copy, (dialog, which) -> copyWordsToClipboard(words))
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                   .show();
  }

  private void onSubmitResult(@NonNull PaymentsRecoveryPhraseViewModel.SubmitResult submitResult) {
    switch (submitResult) {
      case SUCCESS:
        Toast.makeText(requireContext(), R.string.PaymentsRecoveryPhraseFragment__payments_account_restored, Toast.LENGTH_LONG).show();
        Navigation.findNavController(requireView()).popBackStack(R.id.paymentsHome, false);
        break;
      case ERROR:
        new MaterialAlertDialogBuilder(requireContext())
                       .setTitle(R.string.PaymentsRecoveryPhraseFragment__invalid_recovery_phrase)
                       .setMessage(R.string.PaymentsRecoveryPhraseFragment__make_sure_youve_entered_your_phrase_correctly_and_try_again)
                       .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                       .show();
        break;
    }
  }

  /**
   * Zips together a list of MnemonicParts with itself, based off the part count and desired span count.
   *
   * For example, for two spans, 1..12 becomes 1, 7, 2, 8, 3, 9...12
   */
  private static class MnemonicPartComparator implements Comparator<MnemonicPart> {

    private final int partsPerSpan;

    private MnemonicPartComparator(int partCount, int spanCount) {
      this.partsPerSpan = partCount / spanCount;
    }

    @Override
    public int compare(MnemonicPart o1, MnemonicPart o2) {
      int span1 = o1.getIndex() % partsPerSpan;
      int span2 = o2.getIndex() % partsPerSpan;

      if (span1 != span2) {
        return Integer.compare(span1, span2);
      }

      return Integer.compare(o1.getIndex(), o2.getIndex());
    }
  }
}
