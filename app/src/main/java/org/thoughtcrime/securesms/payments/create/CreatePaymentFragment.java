package org.thoughtcrime.securesms.payments.create;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.transition.TransitionManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiTextView;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.MoneyView;
import org.thoughtcrime.securesms.payments.preferences.RecipientHasNotEnabledPaymentsDialog;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.text.DecimalFormatSymbols;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CreatePaymentFragment extends LoggingFragment {

  private static final Map<Integer,AmountKeyboardGlyph> ID_TO_GLYPH = new HashMap<Integer, AmountKeyboardGlyph>() {{
    put(R.id.create_payment_fragment_keyboard_decimal, AmountKeyboardGlyph.DECIMAL);
    put(R.id.create_payment_fragment_keyboard_lt, AmountKeyboardGlyph.BACK);
    put(R.id.create_payment_fragment_keyboard_0, AmountKeyboardGlyph.ZERO);
    put(R.id.create_payment_fragment_keyboard_1, AmountKeyboardGlyph.ONE);
    put(R.id.create_payment_fragment_keyboard_2, AmountKeyboardGlyph.TWO);
    put(R.id.create_payment_fragment_keyboard_3, AmountKeyboardGlyph.THREE);
    put(R.id.create_payment_fragment_keyboard_4, AmountKeyboardGlyph.FOUR);
    put(R.id.create_payment_fragment_keyboard_5, AmountKeyboardGlyph.FIVE);
    put(R.id.create_payment_fragment_keyboard_6, AmountKeyboardGlyph.SIX);
    put(R.id.create_payment_fragment_keyboard_7, AmountKeyboardGlyph.SEVEN);
    put(R.id.create_payment_fragment_keyboard_8, AmountKeyboardGlyph.EIGHT);
    put(R.id.create_payment_fragment_keyboard_9, AmountKeyboardGlyph.NINE);
  }};

  private ConstraintLayout constraintLayout;
  private TextView         balance;
  private MoneyView        amount;
  private TextView         exchange;
  private View             pay;
  private View             request;
  private EmojiTextView    note;
  private View             addNote;
  private View             toggle;
  private Drawable         infoIcon;
  private Drawable         spacer;

  private ConstraintSet cryptoConstraintSet;
  private ConstraintSet fiatConstraintSet;

  public CreatePaymentFragment() {
    super(R.layout.create_payment_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar toolbar = view.findViewById(R.id.create_payment_fragment_toolbar);

    toolbar.setNavigationOnClickListener(this::goBack);

    CreatePaymentFragmentArgs      arguments = CreatePaymentFragmentArgs.fromBundle(requireArguments());
    CreatePaymentViewModel.Factory factory   = new CreatePaymentViewModel.Factory(arguments.getPayee(), arguments.getNote());
    CreatePaymentViewModel         viewModel = new ViewModelProvider(Navigation.findNavController(view).getViewModelStoreOwner(R.id.payments_create), factory).get(CreatePaymentViewModel.class);

    constraintLayout = view.findViewById(R.id.create_payment_fragment_amount_header);
    request          = view.findViewById(R.id.create_payment_fragment_request);
    amount           = view.findViewById(R.id.create_payment_fragment_amount);
    exchange         = view.findViewById(R.id.create_payment_fragment_exchange);
    pay              = view.findViewById(R.id.create_payment_fragment_pay);
    balance          = view.findViewById(R.id.create_payment_fragment_balance);
    note             = view.findViewById(R.id.create_payment_fragment_note);
    addNote          = view.findViewById(R.id.create_payment_fragment_add_note);
    toggle           = view.findViewById(R.id.create_payment_fragment_toggle);

    TextView decimal = view.findViewById(R.id.create_payment_fragment_keyboard_decimal);
    decimal.setText(String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator()));

    View infoTapTarget = view.findViewById(R.id.create_payment_fragment_info_tap_region);

    //noinspection CodeBlock2Expr
    infoTapTarget.setOnClickListener(v -> {
      new MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.CreatePaymentFragment__conversions_are_just_estimates)
          .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
          .setNegativeButton(R.string.LearnMoreTextView_learn_more, (dialog, which) -> {
            dialog.dismiss();
            CommunicationActions.openBrowserLink(requireContext(), getString(R.string.CreatePaymentFragment__learn_more__conversions));
          })
          .show();
         });

    initializeInfoIcon();

    note.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), R.id.action_createPaymentFragment_to_editPaymentNoteFragment));
    addNote.setOnClickListener(v -> SafeNavigation.safeNavigate(Navigation.findNavController(v), R.id.action_createPaymentFragment_to_editPaymentNoteFragment));

    pay.setOnClickListener(v -> {
      NavDirections directions = CreatePaymentFragmentDirections.actionCreatePaymentFragmentToConfirmPaymentFragment(viewModel.getCreatePaymentDetails())
                                                                .setFinishOnConfirm(arguments.getFinishOnConfirm());
      SafeNavigation.safeNavigate(Navigation.findNavController(v), directions);
    });

    toggle.setOnClickListener(v -> viewModel.toggleMoneyInputTarget());

    initializeConstraintSets();
    initializeKeyboardButtons(view, viewModel);

    viewModel.getInputState().observe(getViewLifecycleOwner(), inputState -> {
      updateAmount(inputState);
      updateExchange(inputState);
      updateMoneyInputTarget(inputState.getInputTarget());
    });

    viewModel.getIsPaymentsSupportedByPayee().observe(getViewLifecycleOwner(), isSupported -> {
      if (!isSupported) RecipientHasNotEnabledPaymentsDialog.show(requireContext(), () -> goBack(requireView()));
    });

    viewModel.isValidAmount().observe(getViewLifecycleOwner(), this::updateRequestAmountButtons);
    viewModel.getNote().observe(getViewLifecycleOwner(), this::updateNote);
    viewModel.getSpendableBalance().observe(getViewLifecycleOwner(), this::updateBalance);
    viewModel.getCanSendPayment().observe(getViewLifecycleOwner(), this::updatePayAmountButtons);
    viewModel.getEnclaveFailure().observe(getViewLifecycleOwner(), failure -> {
      if (failure) {
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.PaymentsHomeFragment__update_required))
            .setMessage(getString(R.string.PaymentsHomeFragment__an_update_is_required))
            .setPositiveButton(R.string.PaymentsHomeFragment__update_now, (dialog, which) -> { PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext()); })
            .setNegativeButton(R.string.PaymentsHomeFragment__cancel, (dialog, which) -> {})
            .setCancelable(false)
            .show();
      }
    });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    constraintLayout = null;
    addNote          = null;
    balance          = null;
    amount           = null;
    exchange         = null;
    request          = null;
    toggle           = null;
    note             = null;
    pay              = null;
  }

  private void goBack(View v) {
    if (!Navigation.findNavController(v).popBackStack()) {
      requireActivity().finish();
    }
  }

  private void initializeInfoIcon() {
    spacer   = Objects.requireNonNull(AppCompatResources.getDrawable(requireContext(), R.drawable.payment_info_pad));
    infoIcon = Objects.requireNonNull(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_update_info_16));

    DrawableCompat.setTint(infoIcon, exchange.getCurrentTextColor());

    spacer.setBounds(0, 0, ViewUtil.dpToPx(8), ViewUtil.dpToPx(16));
    infoIcon.setBounds(0, 0, ViewUtil.dpToPx(16), ViewUtil.dpToPx(16));
  }

  private void updateNote(@Nullable CharSequence note) {
    boolean hasNote = !TextUtils.isEmpty(note);
    addNote.setVisibility(hasNote ? View.GONE : View.VISIBLE);
    this.note.setVisibility(hasNote ? View.VISIBLE : View.GONE);
    this.note.setText(note);
  }

  private void initializeKeyboardButtons(@NonNull View view, @NonNull CreatePaymentViewModel viewModel) {
    for (Map.Entry<Integer, AmountKeyboardGlyph> entry : ID_TO_GLYPH.entrySet()) {
      view.findViewById(entry.getKey()).setOnClickListener(v -> viewModel.updateAmount(requireContext(), entry.getValue()));
    }

    view.findViewById(R.id.create_payment_fragment_keyboard_lt).setOnLongClickListener(v -> {
      viewModel.clearAmount();
      return true;
    });
  }

  private void updateAmount(@NonNull InputState inputState) {
    switch (inputState.getInputTarget()) {
      case MONEY:
        amount.setMoney(inputState.getMoneyAmount(), inputState.getMoney().getCurrency());
        break;
      case FIAT_MONEY:
        amount.setMoney(inputState.getMoney(), false, inputState.getExchangeRate().get().getTimestamp());
        amount.append(SpanUtil.buildImageSpan(spacer));
        amount.append(SpanUtil.buildImageSpan(infoIcon));
        break;
    }
  }

  private void updateExchange(@NonNull InputState inputState) {
    switch (inputState.getInputTarget()) {
      case MONEY:
        if (inputState.getFiatMoney().isPresent()) {
          exchange.setVisibility(View.VISIBLE);
          exchange.setText(FiatMoneyUtil.format(getResources(), inputState.getFiatMoney().get(), FiatMoneyUtil.formatOptions().withDisplayTime(true)));
          exchange.append(SpanUtil.buildImageSpan(spacer));
          exchange.append(SpanUtil.buildImageSpan(infoIcon));
          toggle.setVisibility(View.VISIBLE);
          toggle.setEnabled(true);
        } else {
          exchange.setVisibility(View.INVISIBLE);
          toggle.setVisibility(View.INVISIBLE);
          toggle.setEnabled(false);
        }
        break;
      case FIAT_MONEY:
        Currency currency = inputState.getFiatMoney().get().getCurrency();
        exchange.setText(FiatMoneyUtil.manualFormat(currency, inputState.getFiatAmount()));
        break;
    }
  }

  private void updateRequestAmountButtons(boolean isValidAmount) {
    request.setEnabled(isValidAmount);
  }

  private void updatePayAmountButtons(boolean isValidAmount) {
    pay.setEnabled(isValidAmount);
  }

  private void updateBalance(@NonNull Money balance) {
    this.balance.setText(getString(R.string.CreatePaymentFragment__available_balance_s, balance.toString(FormatterOptions.defaults())));
  }

  private void initializeConstraintSets() {
    cryptoConstraintSet = new ConstraintSet();
    cryptoConstraintSet.clone(constraintLayout);

    fiatConstraintSet = new ConstraintSet();
    fiatConstraintSet.clone(getContext(), R.layout.create_payment_fragment_amount_toggle);
  }

  private void updateMoneyInputTarget(@NonNull InputTarget target) {
    TransitionManager.endTransitions(constraintLayout);
    TransitionManager.beginDelayedTransition(constraintLayout);

    switch (target) {
      case FIAT_MONEY:
        fiatConstraintSet.applyTo(constraintLayout);
        amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_secondary));
        exchange.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_primary));
        break;
      case MONEY:
        cryptoConstraintSet.applyTo(constraintLayout);
        exchange.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_secondary));
        amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_primary));
        break;
    }
  }
}
