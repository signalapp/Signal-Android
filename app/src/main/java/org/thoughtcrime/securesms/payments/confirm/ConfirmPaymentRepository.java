package org.thoughtcrime.securesms.payments.confirm;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobs.PaymentSendJob;
import org.thoughtcrime.securesms.payments.Balance;
import org.thoughtcrime.securesms.payments.MobileCoinPublicAddress;
import org.thoughtcrime.securesms.payments.Payee;
import org.thoughtcrime.securesms.payments.PaymentsAddressException;
import org.thoughtcrime.securesms.payments.Wallet;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.payments.Money;

import java.io.IOException;
import java.util.UUID;

final class ConfirmPaymentRepository {

  private static final String TAG = Log.tag(ConfirmPaymentRepository.class);

  private final Wallet wallet;

  ConfirmPaymentRepository(@NonNull Wallet wallet) {
    this.wallet = wallet;
  }

  @AnyThread
  void confirmPayment(@NonNull ConfirmPaymentState state, @NonNull Consumer<ConfirmPaymentResult> consumer) {
    Log.i(TAG, "confirmPayment");
    SignalExecutors.BOUNDED.execute(() -> {
      Balance balance = wallet.getCachedBalance();

      if (state.getTotal().requireMobileCoin().greaterThan(balance.getFullAmount().requireMobileCoin())) {
        Log.w(TAG, "The total was greater than the wallet's balance");
        consumer.accept(new ConfirmPaymentResult.Error());
        return;
      }

      Payee                   payee = state.getPayee();
      RecipientId             recipientId;
      MobileCoinPublicAddress mobileCoinPublicAddress;

      if (payee.hasRecipientId()) {
        recipientId = payee.requireRecipientId();
        try {
          mobileCoinPublicAddress = ProfileUtil.getAddressForRecipient(Recipient.resolved(recipientId));
        } catch (IOException e) {
          Log.w(TAG, "Failed to get address for recipient " + recipientId);
          consumer.accept(new ConfirmPaymentResult.Error());
          return;
        } catch (PaymentsAddressException e) {
          Log.w(TAG, "Failed to get address for recipient " + recipientId);
          consumer.accept(new ConfirmPaymentResult.Error(e.getCode()));
          return;
        }
      } else if (payee.hasPublicAddress()) {
        recipientId             = null;
        mobileCoinPublicAddress = payee.requirePublicAddress();
      } else throw new AssertionError();

      UUID paymentUuid = PaymentSendJob.enqueuePayment(recipientId,
                                                       mobileCoinPublicAddress,
                                                       Util.emptyIfNull(state.getNote()),
                                                       state.getAmount().requireMobileCoin(),
                                                       state.getFee().requireMobileCoin());

      Log.i(TAG, "confirmPayment: PaymentSendJob enqueued");
      consumer.accept(new ConfirmPaymentResult.Success(paymentUuid));
    });
  }

  @WorkerThread
  @NonNull GetFeeResult getFee(@NonNull Money amount) {
    try {
      return new GetFeeResult.Success(wallet.getFee(amount.requireMobileCoin()));
    } catch (IOException e) {
      return new GetFeeResult.Error();
    }
  }

  static class ConfirmPaymentResult {

    static class Success extends ConfirmPaymentResult {
      private final UUID paymentId;

      Success(@NonNull UUID paymentId) {
        this.paymentId = paymentId;
      }

      @NonNull UUID getPaymentId() {
        return paymentId;
      }
    }

    static class Error extends ConfirmPaymentResult {
      private final PaymentsAddressException.Code code;

      Error() {
        this(null);
      }

      Error(@Nullable PaymentsAddressException.Code code) {
        this.code = code;
      }

      public @Nullable PaymentsAddressException.Code getCode() {
        return code;
      }
    }
  }

  static class GetFeeResult {

    static class Success extends GetFeeResult {

      private final Money fee;

      Success(@NonNull Money fee) {
        this.fee = fee;
      }

      @NonNull Money getFee() {
        return fee;
      }
    }

    static class Error extends GetFeeResult {

    }
  }
}
