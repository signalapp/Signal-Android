package org.thoughtcrime.securesms.payments.preferences.details;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.thoughtcrime.securesms.database.PaymentTable;
import org.thoughtcrime.securesms.payments.PaymentTransactionLiveData;
import org.thoughtcrime.securesms.payments.UnreadPaymentsRepository;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Locale;
import java.util.UUID;

final class PaymentsDetailsViewModel extends ViewModel {

  private final LiveData<ViewState> viewState;
  private final LiveData<Boolean>   paymentExists;

  PaymentsDetailsViewModel(@NonNull UUID paymentId) {
    PaymentTransactionLiveData source = new PaymentTransactionLiveData(paymentId);

    LiveData<Recipient> recipientLiveData = Transformations.switchMap(source,
                                                                      payment -> payment != null && payment.getPayee().hasRecipientId() ? Recipient.live(payment.getPayee().requireRecipientId()).getLiveData()
                                                                                                                                        : LiveDataUtil.just(Recipient.UNKNOWN));

    this.viewState     = LiveDataUtil.combineLatest(source, recipientLiveData, ViewState::new);
    this.paymentExists = Transformations.map(source, s -> s != null);

    new UnreadPaymentsRepository().markPaymentSeen(paymentId);
  }

  LiveData<ViewState> getViewState() {
    return viewState;
  }

  LiveData<Boolean> getPaymentExists() {
    return paymentExists;
  }

  static class ViewState {

    private final PaymentTable.PaymentTransaction payment;
    private final Recipient                       recipient;

    private ViewState(@NonNull PaymentTable.PaymentTransaction payment, @NonNull Recipient recipient) {
      this.payment   = payment;
      this.recipient = recipient;
    }

    Recipient getRecipient() {
      return recipient;
    }

    PaymentTable.PaymentTransaction getPayment() {
      return payment;
    }

    String getDate() {
      return DateUtils.formatDate(Locale.getDefault(), payment.getDisplayTimestamp());
    }

    String getTime(@NonNull Context context) {
      return DateUtils.getTimeString(context, Locale.getDefault(), payment.getDisplayTimestamp());
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {
    private final UUID paymentId;

    public Factory(@NonNull UUID paymentId) {
      this.paymentId = paymentId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new PaymentsDetailsViewModel(paymentId));
    }
  }
}
