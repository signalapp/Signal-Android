package org.thoughtcrime.securesms.payments.confirm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.signal.core.util.ThreadUtil;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.CanNotSendPaymentDialog;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.preferences.RecipientHasNotEnabledPaymentsDialog;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.StringUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;
import org.whispersystems.signalservice.api.payments.FormatterOptions;

import java.util.concurrent.TimeUnit;

public class ConfirmPaymentFragment extends BottomSheetDialogFragment {

  private       ConfirmPaymentViewModel viewModel;
  private final Runnable                dismiss = () -> {
    dismissAllowingStateLoss();

    if (ConfirmPaymentFragmentArgs.fromBundle(requireArguments()).getFinishOnConfirm()) {
      requireActivity().setResult(Activity.RESULT_OK);
      requireActivity().finish();
    } else {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), R.id.action_directly_to_paymentsHome);
    }
  };

  @Override
  public void show(@NonNull FragmentManager manager, @Nullable String tag) {
    BottomSheetUtil.show(manager, tag, this);
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    setStyle(DialogFragment.STYLE_NORMAL, R.style.Signal_DayNight_BottomSheet_Rounded);
    super.onCreate(savedInstanceState);
  }

  @Override
  public @NonNull Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.getBehavior().setHideable(false);
    return dialog;
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.confirm_payment_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    ConfirmPaymentViewModel.Factory factory = new ConfirmPaymentViewModel.Factory(ConfirmPaymentFragmentArgs.fromBundle(requireArguments()).getCreatePaymentDetails());
    viewModel = ViewModelProviders.of(this, factory).get(ConfirmPaymentViewModel.class);

    RecyclerView          list    = view.findViewById(R.id.confirm_payment_fragment_list);
    ConfirmPaymentAdapter adapter = new ConfirmPaymentAdapter(new Callbacks());
    list.setAdapter(adapter);

    viewModel.getState().observe(getViewLifecycleOwner(), state -> adapter.submitList(createList(state)));
    viewModel.isPaymentDone().observe(getViewLifecycleOwner(), isDone -> {
      if (isDone) {
        ThreadUtil.runOnMainDelayed(dismiss, TimeUnit.SECONDS.toMillis(2));
      }
    });

    viewModel.getErrorTypeEvents().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case NO_PROFILE_KEY:
          CanNotSendPaymentDialog.show(requireContext());
          break;
        case NO_ADDRESS:
          RecipientHasNotEnabledPaymentsDialog.show(requireContext());
          break;
        case CAN_NOT_GET_FEE:
          new AlertDialog.Builder(requireContext())
                         .setMessage(R.string.ConfirmPaymentFragment__unable_to_request_a_network_fee)
                         .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                           dialog.dismiss();
                           viewModel.refreshFee();
                         })
                         .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                           dialog.dismiss();
                           dismiss();
                         })
                         .setCancelable(false)
                         .show();
          break;
      }
    });
  }

  @Override
  public void onDismiss(@NonNull DialogInterface dialog) {
    super.onDismiss(dialog);
    ThreadUtil.cancelRunnableOnMain(dismiss);
  }

  private @NonNull MappingModelList createList(@NonNull ConfirmPaymentState state) {
    MappingModelList list      = new MappingModelList();
    FormatterOptions options   = FormatterOptions.defaults();

    switch (state.getFeeStatus()) {
      case STILL_LOADING:
      case ERROR:
        list.add(new ConfirmPaymentAdapter.LoadingItem());
        break;
      case NOT_SET:
      case SET:
        list.add(new ConfirmPaymentAdapter.LineItem(getToPayeeDescription(requireContext(), state), state.getAmount().toString(options)));
        if (state.getExchange() != null) {
          list.add(new ConfirmPaymentAdapter.LineItem(getString(R.string.ConfirmPayment__estimated_s, state.getExchange().getCurrency().getCurrencyCode()),
                                                      FiatMoneyUtil.format(getResources(), state.getExchange(), FiatMoneyUtil.formatOptions().withDisplayTime(false))));
        }
        list.add(new ConfirmPaymentAdapter.LineItem(getString(R.string.ConfirmPayment__network_fee), state.getFee().toString(options)));
        list.add(new ConfirmPaymentAdapter.Divider());
        list.add(new ConfirmPaymentAdapter.TotalLineItem(getString(R.string.ConfirmPayment__total_amount), state.getTotal().toString(options)));
    }

    list.add(new ConfirmPaymentAdapter.ConfirmPaymentStatus(state.getStatus(), state.getFeeStatus(), state.getBalance()));
    return list;
  }

  private static CharSequence getToPayeeDescription(Context context, @NonNull ConfirmPaymentState state) {
    return new SpannableStringBuilder().append(context.getString(R.string.ConfirmPayment__to))
                                       .append(' ')
                                       .append(getPayeeDescription(context, state.getPayee()));
  }

  private static CharSequence getPayeeDescription(Context context, @NonNull Payee payee) {
    return payee.hasRecipientId() ? Recipient.resolved(payee.requireRecipientId()).getDisplayName(context)
                                  : mono(context, StringUtil.abbreviateInMiddle(payee.requirePublicAddress().getPaymentAddressBase58(), 17));
  }

  private static CharSequence mono(Context context, CharSequence address) {
    SpannableString spannable = new SpannableString(address);
    spannable.setSpan(new TextAppearanceSpan(context, R.style.TextAppearance_Signal_Mono),
                      0,
                      address.length(),
                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  private class Callbacks implements ConfirmPaymentAdapter.Callbacks {
    @Override
    public void onConfirmPayment() {
      setCancelable(false);
      viewModel.confirmPayment();
    }
  }
}
