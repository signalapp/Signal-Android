package org.thoughtcrime.securesms.payments.preferences.details;

import android.content.Context;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.database.PaymentTable;
import org.thoughtcrime.securesms.payments.Direction;
import org.thoughtcrime.securesms.payments.MoneyView;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.State;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.Locale;
import java.util.Objects;

public final class PaymentDetailsFragment extends LoggingFragment {

  private static final String TAG = Log.tag(PaymentDetailsFragment.class);

  public PaymentDetailsFragment() {
    super(R.layout.payment_details_fragment);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Toolbar toolbar = view.findViewById(R.id.payments_details_toolbar);

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    PaymentDetailsParcelable details = PaymentDetailsFragmentArgs.fromBundle(requireArguments()).getPaymentDetails();

    AvatarImageView   avatar          = view.findViewById(R.id.payments_details_avatar);
    BadgeImageView    badge           = view.findViewById(R.id.payments_details_badge);
    TextView          contactFromTo   = view.findViewById(R.id.payments_details_contact_to_from);
    MoneyView         amount          = view.findViewById(R.id.payments_details_amount);
    TextView          note            = view.findViewById(R.id.payments_details_note);
    TextView          status          = view.findViewById(R.id.payments_details_status);
    View              sentByHeader    = view.findViewById(R.id.payments_details_sent_by_header);
    TextView          sentBy          = view.findViewById(R.id.payments_details_sent_by);
    LearnMoreTextView transactionInfo = view.findViewById(R.id.payments_details_info);
    TextView          sentTo          = view.findViewById(R.id.payments_details_sent_to_header);
    MoneyView         sentToAmount    = view.findViewById(R.id.payments_details_sent_to_amount);
    View              sentFeeHeader   = view.findViewById(R.id.payments_details_sent_fee_header);
    MoneyView         sentFeeAmount   = view.findViewById(R.id.payments_details_sent_fee_amount);
    Group             sentViews       = view.findViewById(R.id.payments_details_sent_views);
    View              blockHeader     = view.findViewById(R.id.payments_details_block_header);
    TextView          blockNumber     = view.findViewById(R.id.payments_details_block);

    if (details.hasPayment()) {
      Payment payment = details.requirePayment();
      avatar.disableQuickContact();
      avatar.setImageResource(R.drawable.ic_mobilecoin_avatar_24);
      contactFromTo.setText(getContactFromToTextFromDirection(payment.getDirection()));
      amount.setMoney(payment.getAmountPlusFeeWithDirection());
      note.setVisibility(View.GONE);
      status.setText(getStatusFromPayment(payment));
      sentByHeader.setVisibility(View.GONE);
      sentBy.setVisibility(View.GONE);
      transactionInfo.setLearnMoreVisible(true);
      transactionInfo.setText(R.string.PaymentsDetailsFragment__information);
      transactionInfo.setLink(getString(R.string.PaymentsDetailsFragment__learn_more__information));
      sentTo.setVisibility(View.GONE);
      sentToAmount.setVisibility(View.GONE);
      blockHeader.setVisibility(View.VISIBLE);
      blockNumber.setVisibility(View.VISIBLE);
      blockNumber.setText(String.valueOf(payment.getBlockIndex()));

      if (payment.getDirection() == Direction.SENT) {
        sentFeeAmount.setMoney(payment.getFee());
        sentFeeHeader.setVisibility(View.VISIBLE);
        sentFeeAmount.setVisibility(View.VISIBLE);
      }
    } else {
      PaymentsDetailsViewModel viewModel = new ViewModelProvider(this, new PaymentsDetailsViewModel.Factory(details.requireUuid())).get(PaymentsDetailsViewModel.class);
      viewModel.getViewState()
               .observe(getViewLifecycleOwner(),
                        state -> {
                          if (state.getRecipient().getId().isUnknown() || state.getPayment().isDefrag()) {
                            avatar.disableQuickContact();
                            avatar.setImageResource(R.drawable.ic_mobilecoin_avatar_24);
                          } else {
                            avatar.setRecipient(state.getRecipient(), true);
                            badge.setBadgeFromRecipient(state.getRecipient());
                          }
                          contactFromTo.setText(describeToOrFrom(state));

                          if (state.getPayment().getState() == State.FAILED) {
                            amount.setTextColor(ContextCompat.getColor(requireContext(), R.color.signal_text_primary_disabled));
                            amount.setMoney(state.getPayment().getAmountPlusFeeWithDirection(), false);
                            transactionInfo.setVisibility(View.GONE);
                          } else {
                            amount.setMoney(state.getPayment().getAmountPlusFeeWithDirection());
                            if (state.getPayment().isDefrag()) {
                              transactionInfo.setLearnMoreVisible(true);
                              transactionInfo.setText(R.string.PaymentsDetailsFragment__coin_cleanup_information);
                              transactionInfo.setLink(getString(R.string.PaymentsDetailsFragment__learn_more__cleanup_fee));
                            } else {
                              transactionInfo.setLearnMoreVisible(true);
                              transactionInfo.setText(R.string.PaymentsDetailsFragment__information);
                              transactionInfo.setLink(getString(R.string.PaymentsDetailsFragment__learn_more__information));
                            }
                            transactionInfo.setVisibility(View.VISIBLE);
                          }

                          String trimmedNote = state.getPayment().getNote().trim();
                          note.setText(trimmedNote);
                          note.setVisibility(TextUtils.isEmpty(trimmedNote) ? View.GONE : View.VISIBLE);
                          status.setText(describeStatus(state.getPayment()));
                          sentBy.setText(describeSentBy(state));
                          if (state.getPayment().getDirection().isReceived()) {
                            sentToAmount.setMoney(Money.MobileCoin.ZERO);
                            sentFeeAmount.setMoney(Money.MobileCoin.ZERO);
                            sentViews.setVisibility(View.GONE);
                          } else {
                            sentTo.setText(describeSentTo(state, state.getPayment()));
                            sentToAmount.setMoney(state.getPayment().getAmount());
                            sentFeeAmount.setMoney(state.getPayment().getFee());
                            sentViews.setVisibility(View.VISIBLE);
                          }
                        }
               );

      viewModel.getPaymentExists()
               .observe(getViewLifecycleOwner(), exists -> {
                 if (!exists) {
                   Log.w(TAG, "Failed to find payment detail");
                   FragmentActivity fragmentActivity = requireActivity();
                   fragmentActivity.onBackPressed();
                   Toast.makeText(fragmentActivity, R.string.PaymentsDetailsFragment__no_details_available, Toast.LENGTH_SHORT).show();
                 }
               });
    }
  }

  private CharSequence describeToOrFrom(PaymentsDetailsViewModel.ViewState state) {
    if (state.getPayment().isDefrag()) {
      return getString(R.string.PaymentsDetailsFragment__coin_cleanup_fee);
    }
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
    switch (state.getPayment().getDirection()) {
      case SENT:
        stringBuilder.append(getString(R.string.PaymentsDetailsFragment__to));
        break;
      case RECEIVED:
        stringBuilder.append(getString(R.string.PaymentsDetailsFragment__from));
        break;
      default:
        throw new AssertionError();
    }

    stringBuilder.append(' ').append(describe(state.getPayment().getPayee(), state.getRecipient()));
    return stringBuilder;
  }

  private @NonNull CharSequence describe(@NonNull Payee payee, @NonNull Recipient recipient) {
    if (payee.hasRecipientId()) {
      return recipient.getDisplayName(requireContext());
    } else if (payee.hasPublicAddress()) {
      return mono(requireContext(), Objects.requireNonNull(StringUtil.abbreviateInMiddle(payee.requirePublicAddress().getPaymentAddressBase58(), 17)));
    } else {
      throw new AssertionError();
    }
  }

  private static @NonNull CharSequence mono(@NonNull Context context, @NonNull CharSequence address) {
    SpannableString spannable = new SpannableString(address);
    spannable.setSpan(new TextAppearanceSpan(context, R.style.TextAppearance_Signal_Mono),
                      0,
                      address.length(),
                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  private CharSequence describeSentBy(PaymentsDetailsViewModel.ViewState state) {
    switch (state.getPayment().getDirection()) {
      case SENT:
        return getResources().getString(R.string.PaymentsDetailsFragment__you_on_s_at_s, state.getDate(), state.getTime(requireContext()));
      case RECEIVED:
        return SpanUtil.replacePlaceHolder(getResources().getString(R.string.PaymentsDetailsFragment__s_on_s_at_s, SpanUtil.SPAN_PLACE_HOLDER, state.getDate(), state.getTime(requireContext())),
                                           describe(state.getPayment().getPayee(), state.getRecipient()));
      default:
        throw new AssertionError();
    }
  }

  private @NonNull CharSequence describeSentTo(@NonNull PaymentsDetailsViewModel.ViewState state, @NonNull PaymentTable.PaymentTransaction payment) {
    if (payment.getDirection().isSent()) {
      return SpanUtil.insertSingleSpan(getResources(), R.string.PaymentsDetailsFragment__sent_to_s, describe(payment.getPayee(), state.getRecipient()));
    } else {
      throw new AssertionError();
    }
  }

  private @NonNull CharSequence describeStatus(@NonNull PaymentTable.PaymentTransaction payment) {
    switch (payment.getState()) {
      case INITIAL:
        return getResources().getString(R.string.PaymentsDetailsFragment__submitting_payment);
      case SUBMITTED:
        return getResources().getString(R.string.PaymentsDetailsFragment__processing_payment);
      case SUCCESSFUL:
        return getResources().getString(R.string.PaymentsDetailsFragment__payment_complete);
      case FAILED:
        return SpanUtil.color(ContextCompat.getColor(requireContext(), R.color.signal_alert_primary), getResources().getString(R.string.PaymentsDetailsFragment__payment_failed));
      default:
        throw new AssertionError();
    }
  }

  private @NonNull CharSequence getContactFromToTextFromDirection(@NonNull Direction direction) {
    switch (direction) {
      case SENT:
        return getResources().getString(R.string.PaymentsDetailsFragment__sent_payment);
      case RECEIVED:
        return getResources().getString(R.string.PaymentsDetailsFragment__received_payment);
      default:
        throw new AssertionError();
    }
  }

  private @NonNull CharSequence getStatusFromPayment(@NonNull Payment payment) {
    return getResources().getString(R.string.PaymentsDeatilsFragment__payment_completed_s, DateUtils.getTimeString(requireContext(), Locale.getDefault(), payment.getDisplayTimestamp()));
  }
}
