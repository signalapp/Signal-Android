package org.thoughtcrime.securesms.payments.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.core.util.money.FiatMoney;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.SettingHeader;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.PaymentsAvailability;
import org.thoughtcrime.securesms.keyvalue.PaymentsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Balance;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.UnreadPaymentsRepository;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchange;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchangeRepository;
import org.thoughtcrime.securesms.payments.preferences.model.InProgress;
import org.thoughtcrime.securesms.payments.preferences.model.InfoCard;
import org.thoughtcrime.securesms.payments.preferences.model.IntroducingPayments;
import org.thoughtcrime.securesms.payments.preferences.model.NoRecentActivity;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;
import org.thoughtcrime.securesms.payments.preferences.model.SeeAll;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.livedata.Store;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.List;
import java.util.Optional;

public class PaymentsHomeViewModel extends ViewModel {

  private static final String TAG = Log.tag(PaymentsHomeViewModel.class);

  private static final int MAX_PAYMENT_ITEMS = 4;

  private final Store<PaymentsHomeState>           store;
  private final LiveData<MappingModelList>         list;
  private final LiveData<Boolean>                  paymentsEnabled;
  private final LiveData<Money>                    balance;
  private final LiveData<FiatMoney>                exchange;
  private final SingleLiveEvent<PaymentStateEvent> paymentStateEvents;
  private final SingleLiveEvent<ErrorEnabling>     errorEnablingPayments;
  private final LiveData<Boolean>                  enclaveFailure;

  private final PaymentsHomeRepository     paymentsHomeRepository;
  private final CurrencyExchangeRepository currencyExchangeRepository;
  private final UnreadPaymentsRepository   unreadPaymentsRepository;
  private final LiveData<LoadState>        exchangeLoadState;

  PaymentsHomeViewModel(@NonNull PaymentsHomeRepository paymentsHomeRepository,
                        @NonNull PaymentsRepository paymentsRepository,
                        @NonNull CurrencyExchangeRepository currencyExchangeRepository)
  {
    this.paymentsHomeRepository     = paymentsHomeRepository;
    this.currencyExchangeRepository = currencyExchangeRepository;
    this.unreadPaymentsRepository   = new UnreadPaymentsRepository();
    this.store                      = new Store<>(new PaymentsHomeState(getPaymentsState()));
    this.balance                    = LiveDataUtil.mapDistinct(SignalStore.payments().liveMobileCoinBalance(), Balance::getFullAmount);
    this.list                       = Transformations.map(store.getStateLiveData(), this::createList);
    this.paymentsEnabled            = LiveDataUtil.mapDistinct(store.getStateLiveData(), state -> state.getPaymentsState() == PaymentsHomeState.PaymentsState.ACTIVATED);
    this.exchange                   = LiveDataUtil.mapDistinct(store.getStateLiveData(), PaymentsHomeState::getExchangeAmount);
    this.exchangeLoadState          = LiveDataUtil.mapDistinct(store.getStateLiveData(), PaymentsHomeState::getExchangeRateLoadState);
    this.paymentStateEvents         = new SingleLiveEvent<>();
    this.errorEnablingPayments      = new SingleLiveEvent<>();
    this.enclaveFailure             = LiveDataUtil.mapDistinct(SignalStore.payments().enclaveFailure(), isFailure -> isFailure);
    this.store.update(paymentsRepository.getRecentPayments(), this::updateRecentPayments);

    LiveData<CurrencyExchange.ExchangeRate> liveExchangeRate = LiveDataUtil.combineLatest(SignalStore.payments().liveCurrentCurrency(),
                                                                                          LiveDataUtil.mapDistinct(store.getStateLiveData(), PaymentsHomeState::getCurrencyExchange),
                                                                                          (currency, exchange) -> exchange.getExchangeRate(currency));

    LiveData<Optional<FiatMoney>> liveExchangeAmount = LiveDataUtil.combineLatest(this.balance,
                                                                                  liveExchangeRate,
                                                                                  (balance, exchangeRate) -> exchangeRate.exchange(balance));
    this.store.update(liveExchangeAmount, (amount, state) -> state.updateCurrencyAmount(amount.orElse(null)));

    refreshExchangeRates(true);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    store.clear();
  }

  private static PaymentsHomeState.PaymentsState getPaymentsState() {
    PaymentsValues paymentsValues = SignalStore.payments();

    PaymentsAvailability paymentsAvailability = paymentsValues.getPaymentsAvailability();

    if (paymentsAvailability.canRegister()) {
      return PaymentsHomeState.PaymentsState.NOT_ACTIVATED;
    } else if (paymentsAvailability.isEnabled()) {
      return PaymentsHomeState.PaymentsState.ACTIVATED;
    } else {
      return PaymentsHomeState.PaymentsState.ACTIVATE_NOT_ALLOWED;
    }
  }

  @NonNull LiveData<PaymentStateEvent> getPaymentStateEvents() {
    return paymentStateEvents;
  }

  @NonNull LiveData<ErrorEnabling> getErrorEnablingPayments() {
    return errorEnablingPayments;
  }

  @NonNull LiveData<Boolean> getEnclaveFailure() {
    return enclaveFailure;
  }

  @NonNull boolean isEnclaveFailurePresent() {
    return Boolean.TRUE.equals(getEnclaveFailure().getValue());
  }

  @NonNull LiveData<MappingModelList> getList() {
    return list;
  }

  @NonNull LiveData<Boolean> getPaymentsEnabled() {
    return paymentsEnabled;
  }

  @NonNull LiveData<Money> getBalance() {
    return balance;
  }

  @NonNull LiveData<FiatMoney> getExchange() {
    return exchange;
  }

  @NonNull LiveData<LoadState> getExchangeLoadState() {
    return exchangeLoadState;
  }

  void markAllPaymentsSeen() {
    unreadPaymentsRepository.markAllPaymentsSeen();
  }

  void checkPaymentActivationState() {
    PaymentsHomeState.PaymentsState storedState     = store.getState().getPaymentsState();
    boolean                         paymentsEnabled = SignalStore.payments().mobileCoinPaymentsEnabled();

    if (storedState.equals(PaymentsHomeState.PaymentsState.ACTIVATED) && !paymentsEnabled) {
      store.update(s -> s.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.NOT_ACTIVATED));
      paymentStateEvents.setValue(PaymentStateEvent.DEACTIVATED);
    } else if (storedState.equals(PaymentsHomeState.PaymentsState.NOT_ACTIVATED) && paymentsEnabled) {
      store.update(s -> s.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.ACTIVATED));
      paymentStateEvents.setValue(PaymentStateEvent.ACTIVATED);
    }
  }

  private @NonNull MappingModelList createList(@NonNull PaymentsHomeState state) {
    MappingModelList list = new MappingModelList();

    if (state.getPaymentsState() == PaymentsHomeState.PaymentsState.ACTIVATED) {
      if (state.getTotalPayments() > 0) {
        list.add(new SettingHeader.Item(R.string.PaymentsHomeFragment__recent_activity));
        list.addAll(state.getPayments());
        if (state.getTotalPayments() > MAX_PAYMENT_ITEMS) {
          list.add(new SeeAll(PaymentType.PAYMENT));
        }
      }

      if (!state.isRecentPaymentsLoaded()) {
        list.add(new InProgress());
      } else if (state.getRequests().isEmpty() &&
                 state.getPayments().isEmpty() &&
                 state.isRecentPaymentsLoaded())
      {
        list.add(new NoRecentActivity());
      }
    } else if (state.getPaymentsState() == PaymentsHomeState.PaymentsState.ACTIVATE_NOT_ALLOWED) {
      Log.w(TAG, "Payments remotely disabled or not in region");
    } else {
      list.add(new IntroducingPayments(state.getPaymentsState()));
    }

    list.addAll(InfoCard.getInfoCards());

    return list;
  }

  private @NonNull PaymentsHomeState updateRecentPayments(@NonNull List<Payment> payments,
                                                          @NonNull PaymentsHomeState state)
  {
    List<PaymentItem> paymentItems = Stream.of(payments)
                                           .limit(MAX_PAYMENT_ITEMS)
                                           .map(PaymentItem::fromPayment)
                                           .toList();

    return state.updatePayments(paymentItems, payments.size());
  }

  public void updateStore() {
    store.update(s -> s);
  }

  public void activatePayments() {
    if (store.getState().getPaymentsState() != PaymentsHomeState.PaymentsState.NOT_ACTIVATED) {
      return;
    }

    store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.ACTIVATING));

    paymentsHomeRepository.activatePayments(new AsynchronousCallback.WorkerThread<Void, PaymentsHomeRepository.Error>() {
      @Override
      public void onComplete(@Nullable Void result) {
        store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.ACTIVATED));
        paymentStateEvents.postValue(PaymentStateEvent.ACTIVATED);
      }

      @Override
      public void onError(@Nullable PaymentsHomeRepository.Error error) {
        store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.NOT_ACTIVATED));
        if (error == PaymentsHomeRepository.Error.NetworkError) {
          errorEnablingPayments.postValue(ErrorEnabling.NETWORK);
        } else if (error == PaymentsHomeRepository.Error.RegionError) {
          errorEnablingPayments.postValue(ErrorEnabling.REGION);
        } else {
          throw new AssertionError();
        }
      }
    });
  }

  public void deactivatePayments() {
    Money money = balance.getValue();
    if (money == null) {
      paymentStateEvents.setValue(PaymentStateEvent.NO_BALANCE);
    } else if (money.isPositive()) {
      paymentStateEvents.setValue(PaymentStateEvent.DEACTIVATE_WITH_BALANCE);
    } else {
      paymentStateEvents.setValue(PaymentStateEvent.DEACTIVATE_WITHOUT_BALANCE);
    }
  }

  public void confirmDeactivatePayments() {
    if (store.getState().getPaymentsState() != PaymentsHomeState.PaymentsState.ACTIVATED) {
      return;
    }

    store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.DEACTIVATING));

    paymentsHomeRepository.deactivatePayments(result -> {
      store.update(state -> state.updatePaymentsEnabled(result ? PaymentsHomeState.PaymentsState.NOT_ACTIVATED : PaymentsHomeState.PaymentsState.ACTIVATED));

      if (result) {
       paymentStateEvents.postValue(PaymentStateEvent.DEACTIVATED);
      }
    });
  }

  public void refreshExchangeRates(boolean refreshIfAble) {
    store.update(state -> state.updateExchangeRateLoadState(LoadState.LOADING));
    currencyExchangeRepository.getCurrencyExchange(new AsynchronousCallback.WorkerThread<CurrencyExchange, Throwable>() {
      @Override
      public void onComplete(@Nullable CurrencyExchange result) {
        store.update(state -> state.updateCurrencyExchange(result, LoadState.LOADED));
      }

      @Override
      public void onError(@Nullable Throwable error) {
        Log.w(TAG, error);
        store.update(state -> state.updateExchangeRateLoadState(LoadState.ERROR));
      }
    }, refreshIfAble);
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new PaymentsHomeViewModel(new PaymentsHomeRepository(),
                                                       new PaymentsRepository(),
                                                       new CurrencyExchangeRepository(AppDependencies.getPayments())));
    }
  }

  public enum ErrorEnabling {
    REGION,
    NETWORK
  }
}
