package org.thoughtcrime.securesms.conversationlist.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.UUID;

/**
 * UnreadPayments encapsulates information required by the view layer to render and interact with an UnreadPaymentsView.
 * This class is intentionally abstract with a private constructor to prevent other subclasses from being created.
 */
public abstract class UnreadPayments {

  private UnreadPayments() {}

  public abstract @NonNull String getDescription(@NonNull Context context);

  public abstract @Nullable Recipient getRecipient();

  public abstract @Nullable UUID getPaymentUuid();

  public abstract int getUnreadCount();

  public static @NonNull UnreadPayments forSingle(@Nullable Recipient recipient, @NonNull UUID paymentId, @NonNull Money amount) {
    return new SingleRecipient(recipient, paymentId, amount);
  }

  public static @NonNull UnreadPayments forMultiple(int unreadCount) {
    return new MultipleRecipients(unreadCount);
  }

  private static final class SingleRecipient extends UnreadPayments {

    private final Recipient recipient;
    private final UUID      paymentId;
    private final Money     amount;

    private SingleRecipient(@Nullable Recipient recipient, @NonNull UUID paymentId, @NonNull Money amount) {
      this.recipient = recipient;
      this.paymentId = paymentId;
      this.amount    = amount;
    }

    @Override
    public @NonNull String getDescription(@NonNull Context context) {
      if (recipient != null) {
        return context.getString(R.string.UnreadPayments__s_sent_you_s,
                                 recipient.getShortDisplayName(context),
                                 amount.toString(FormatterOptions.defaults()));
      } else {
        return context.getString(R.string.UnreadPayments__d_new_payment_notifications, 1);
      }
    }

    @Override
    public @Nullable Recipient getRecipient() {
      return recipient;
    }

    @Override
    public @Nullable UUID getPaymentUuid() {
      return paymentId;
    }

    @Override
    public int getUnreadCount() {
      return 1;
    }
  }

  private static final class MultipleRecipients extends UnreadPayments {

    private final int unreadCount;

    private MultipleRecipients(int unreadCount) {
      this.unreadCount = unreadCount;
    }

    @Override
    public @NonNull String getDescription(@NonNull Context context) {
      return context.getString(R.string.UnreadPayments__d_new_payment_notifications, unreadCount);
    }

    @Override
    public @Nullable Recipient getRecipient() {
      return null;
    }

    @Override
    public @Nullable UUID getPaymentUuid() {
      return null;
    }

    @Override
    public int getUnreadCount() {
      return unreadCount;
    }
  }
}
