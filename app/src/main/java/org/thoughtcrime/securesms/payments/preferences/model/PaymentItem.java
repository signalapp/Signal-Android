package org.thoughtcrime.securesms.payments.preferences.model;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.ReconstructedPayment;
import org.thoughtcrime.securesms.payments.State;
import org.thoughtcrime.securesms.payments.preferences.PaymentType;
import org.thoughtcrime.securesms.payments.preferences.details.PaymentDetailsParcelable;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.PaymentsConstants;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel.RecipientIdMappingModel;

public final class PaymentItem implements MappingModel<PaymentItem> {

  private final Payment     payment;
  private final PaymentType paymentType;

  public static @NonNull MappingModelList fromPayment(@NonNull List<Payment> transactions) {
    return Stream.of(transactions)
                 .map(PaymentItem::fromPayment)
                 .collect(MappingModelList.toMappingModelList());
  }

  public static @NonNull PaymentItem fromPayment(@NonNull Payment transaction) {
    return new PaymentItem(transaction,
                           PaymentType.PAYMENT);
  }

  private PaymentItem(@NonNull Payment payment,
                      @NonNull PaymentType paymentType)
  {
    this.payment     = payment;
    this.paymentType = paymentType;
  }

  public @NonNull PaymentDetailsParcelable getPaymentDetailsParcelable() {
    if (payment instanceof ReconstructedPayment) {
      return PaymentDetailsParcelable.forPayment(payment);
    } else {
      return PaymentDetailsParcelable.forUuid(payment.getUuid());
    }
  }

  public boolean isInProgress() {
    return payment.getState().isInProgress();
  }

  public boolean isUnread() {
    return !payment.isSeen();
  }

  public @NonNull CharSequence getDate(@NonNull Context context) {
    if (isInProgress()) {
      return context.getString(R.string.PaymentsHomeFragment__processing_payment);
    }

    if (payment.getState() == State.FAILED) {
      return SpanUtil.color(ContextCompat.getColor(context, R.color.signal_alert_primary), context.getString(R.string.PaymentsHomeFragment__payment_failed));
    }

    String date   = DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), payment.getDisplayTimestamp());
    int    prefix = payment.getDirection().isReceived() ? R.string.PaymentsHomeFragment__received_s : R.string.PaymentsHomeFragment__sent_s;

    return context.getString(prefix, date);
  }

  public @NonNull String getAmount(@NonNull Context context) {
    if (isInProgress() && payment.getDirection().isReceived()) {
      return context.getString(R.string.PaymentsHomeFragment__unknown_amount);
    }

    if (payment.getState() == State.FAILED) {
      return context.getString(R.string.PaymentsHomeFragment__details);
    }

    return payment.getAmountPlusFeeWithDirection()
                  .toString(FormatterOptions.builder(Locale.getDefault())
                                            .alwaysPrefixWithSign()
                                            .withMaximumFractionDigits(PaymentsConstants.SHORT_FRACTION_LENGTH)
                                            .build());
  }

  public @ColorRes int getAmountColor() {
    if (isInProgress()) {
      return R.color.signal_text_primary_disabled;
    } else if (payment.getState() == State.FAILED) {
      return R.color.signal_text_secondary;
    } else if (paymentType == PaymentType.REQUEST) {
      return R.color.core_grey_45;
    } else if (payment.getDirection().isReceived()) {
      return R.color.core_green;
    } else {
      return R.color.signal_text_primary;
    }
  }

  public boolean isDefrag() {
    return payment.isDefrag();
  }

  public boolean hasRecipient() {
    return payment.getPayee().hasRecipientId();
  }

  public @Nullable String getTransactionName(@NonNull Context context) {
    return context.getString(payment.isDefrag() ? R.string.PaymentsHomeFragment__coin_cleanup_fee
                                                : payment.getDirection().isSent() ? R.string.PaymentsHomeFragment__sent_payment
                                                                                  : R.string.PaymentsHomeFragment__received_payment);
  }

  public @DrawableRes int getTransactionAvatar() {
    return R.drawable.ic_mobilecoin_avatar_24;
  }

  public @NonNull RecipientIdMappingModel getRecipientIdModel() {
    return new RecipientIdMappingModel(payment.getPayee().requireRecipientId());
  }

  @Override
  public boolean areItemsTheSame(@NonNull PaymentItem newItem) {
    return payment.getUuid().equals(newItem.payment.getUuid());
  }

  @Override
  public boolean areContentsTheSame(@NonNull PaymentItem newItem) {
    return payment.getDisplayTimestamp() == newItem.payment.getDisplayTimestamp() &&
           payment.getAmount().equals(newItem.payment.getAmount()) &&
           paymentType == newItem.paymentType &&
           payment.getDirection() == newItem.payment.getDirection() &&
           payment.getState() == newItem.payment.getState() &&
           Objects.equals(payment.getPayee(), newItem.payment.getPayee()) &&
           payment.isSeen() == newItem.payment.isSeen() &&
           payment.isDefrag() == newItem.payment.isDefrag();
  }
}
