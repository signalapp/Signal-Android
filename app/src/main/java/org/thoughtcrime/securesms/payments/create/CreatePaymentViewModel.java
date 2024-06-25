package org.thoughtcrime.securesms.payments.create;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.signal.core.util.logging.Log;
import org.signal.core.util.money.FiatMoney;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Balance;
import org.thoughtcrime.securesms.payments.CreatePaymentDetails;
import org.thoughtcrime.securesms.payments.FiatMoneyUtil;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchange;
import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DefaultValueLiveData;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.livedata.Store;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.Optional;

public class CreatePaymentViewModel extends ViewModel {

  private static final String TAG = Log.tag(CreatePaymentViewModel.class);

  private static final Money.MobileCoin AMOUNT_LOWER_BOUND_EXCLUSIVE = Money.MobileCoin.ZERO;
  private static final Money.MobileCoin AMOUNT_UPPER_BOUND_EXCLUSIVE = Money.MobileCoin.MAX_VALUE;

  private final LiveData<Money>   spendableBalance;
  private final LiveData<Boolean> isValidAmount;
  private final Store<InputState> inputState;
  private final LiveData<Boolean> isPaymentsSupportedByPayee;
  private final LiveData<Boolean> enclaveFailure;

  private final PayeeParcelable               payee;
  private final MutableLiveData<CharSequence> note;

  private CreatePaymentViewModel(@NonNull PayeeParcelable payee, @Nullable CharSequence note) {
    this.payee            = payee;
    this.spendableBalance = Transformations.map(SignalStore.payments().liveMobileCoinBalance(), Balance::getTransferableAmount);
    this.note             = new MutableLiveData<>(note);
    this.inputState       = new Store<>(new InputState());
    this.isValidAmount    = LiveDataUtil.combineLatest(spendableBalance, inputState.getStateLiveData(), (b, s) -> validateAmount(b.requireMobileCoin(), s.getMoney().requireMobileCoin()));
    this.enclaveFailure   = LiveDataUtil.mapDistinct(SignalStore.payments().enclaveFailure(), isFailure -> isFailure);

    if (payee.getPayee().hasRecipientId()) {
      isPaymentsSupportedByPayee = LiveDataUtil.mapAsync(new DefaultValueLiveData<>(payee.getPayee().requireRecipientId()), r -> {
        try {
          ProfileUtil.getAddressForRecipient(Recipient.resolved(r));
          return true;
        } catch (Exception e) {
          Log.w(TAG, "Could not get address for recipient: ", e);
          return false;
        }
      });
    } else {
      isPaymentsSupportedByPayee = new DefaultValueLiveData<>(true);
    }

    LiveData<Optional<CurrencyExchange.ExchangeRate>> liveExchangeRate = LiveDataUtil.mapAsync(SignalStore.payments().liveCurrentCurrency(),
                                                                                               currency -> {
                                                                                                 try {
                                                                                                   return Optional.ofNullable(AppDependencies.getPayments()
                                                                                                                                             .getCurrencyExchange(true)
                                                                                                                                             .getExchangeRate(currency));
                                                                                                 } catch (IOException e1) {
                                                                                                   Log.w(TAG, "Unable to get fresh exchange data, falling back to cached", e1);
                                                                                                   try {
                                                                                                     return Optional.ofNullable(AppDependencies.getPayments()
                                                                                                                                               .getCurrencyExchange(false)
                                                                                                                                               .getExchangeRate(currency));
                                                                                                   } catch (IOException e2) {
                                                                                                     Log.w(TAG, "Unable to get any exchange data", e2);
                                                                                                     return Optional.empty();
                                                                                                   }
                                                                                                 }
                                                                                               });

    inputState.update(liveExchangeRate, (rate, state) -> updateAmount(AppDependencies.getApplication(), state.updateExchangeRate(rate), AmountKeyboardGlyph.NONE));
  }

  @NonNull LiveData<Boolean> getEnclaveFailure() {
    return enclaveFailure;
  }

  @NonNull LiveData<InputState> getInputState() {
    return inputState.getStateLiveData();
  }

  @NonNull LiveData<Boolean> getIsPaymentsSupportedByPayee() {
    return isPaymentsSupportedByPayee;
  }

  @NonNull LiveData<CharSequence> getNote() {
    return Transformations.distinctUntilChanged(note);
  }

  @NonNull LiveData<Boolean> isValidAmount() {
    return isValidAmount;
  }

  @NonNull LiveData<Boolean> getCanSendPayment() {
    return isValidAmount;
  }

  @NonNull LiveData<Money> getSpendableBalance() {
    return spendableBalance;
  }

  void clearAmount() {
    inputState.update(s -> {
      final Money               money = Money.MobileCoin.ZERO;
      final Optional<FiatMoney> fiat  = s.getExchangeRate().flatMap(r -> r.exchange(money));

      return s.updateAmount("0", "0", Money.MobileCoin.ZERO, fiat);
    });
  }

  void toggleMoneyInputTarget() {
    inputState.update(s -> s.updateInputTarget(s.getInputTarget().next()));
  }

  void setNote(@Nullable CharSequence note) {
    this.note.setValue(note);
  }

  void updateAmount(@NonNull Context context, @NonNull AmountKeyboardGlyph glyph) {
    inputState.update(s -> updateAmount(context, s, glyph));
  }

  private @NonNull InputState updateAmount(@NonNull Context context, @NonNull InputState inputState, @NonNull AmountKeyboardGlyph glyph) {
    switch (inputState.getInputTarget()) {
      case FIAT_MONEY:
        return updateFiatAmount(context, inputState, glyph, SignalStore.payments().currentCurrency());
      case MONEY:
        return updateMoneyAmount(context, inputState, glyph);
      default:
        throw new IllegalStateException("Unexpected input target " + inputState.getInputTarget().name());
    }
  }

  private @NonNull InputState updateFiatAmount(@NonNull Context context,
                                               @NonNull InputState inputState,
                                               @NonNull AmountKeyboardGlyph glyph,
                                               @NonNull Currency currency)
  {
    String    newFiatAmount = updateAmountString(context, inputState.getFiatAmount(), glyph, currency.getDefaultFractionDigits());
    FiatMoney newFiat       = stringToFiatValueOrZero(newFiatAmount, currency);
    Money     newMoney      = inputState.getExchangeRate().flatMap(e -> e.exchange(newFiat)).get();
    String    newMoneyAmount;

    if (newFiatAmount.equals("0")) {
      newMoneyAmount = "0";
    } else {
      newMoneyAmount = newMoney.toString(FormatterOptions.builder().withoutUnit().build());
    }

    if (!withinMobileCoinBounds(newMoney.requireMobileCoin())) {
      return inputState;
    }

    return inputState.updateAmount(newMoneyAmount, newFiatAmount, newMoney, Optional.of(newFiat));
  }

  private @NonNull InputState updateMoneyAmount(@NonNull Context context,
                                                @NonNull InputState inputState,
                                                @NonNull AmountKeyboardGlyph glyph)
  {
    String              newMoneyAmount = updateAmountString(context, inputState.getMoneyAmount(), glyph, inputState.getMoney().getCurrency().getDecimalPrecision());
    Money.MobileCoin    newMoney       = stringToMobileCoinValueOrZero(newMoneyAmount);
    Optional<FiatMoney> newFiat        = inputState.getExchangeRate().flatMap(e -> e.exchange(newMoney));
    String              newFiatAmount;

    if (!withinMobileCoinBounds(newMoney)) {
      return inputState;
    }

    if (newMoneyAmount.equals("0")) {
      newFiatAmount = "0";
    } else {
      newFiatAmount = newFiat.map(f -> FiatMoneyUtil.format(context.getResources(), f, FiatMoneyUtil.formatOptions().withDisplayTime(false).numberOnly())).orElse("0");
    }

    return inputState.updateAmount(newMoneyAmount, newFiatAmount, newMoney, newFiat);
  }

  private boolean validateAmount(@NonNull Money.MobileCoin spendableBalance, @NonNull Money.MobileCoin amount) {
    try {
      return amount.greaterThan(AMOUNT_LOWER_BOUND_EXCLUSIVE) &&
             !amount.greaterThan(spendableBalance);
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private static boolean withinMobileCoinBounds(@NonNull Money.MobileCoin amount) {
    return !amount.lessThan(AMOUNT_LOWER_BOUND_EXCLUSIVE) &&
           !amount.greaterThan(AMOUNT_UPPER_BOUND_EXCLUSIVE);
  }

  private @NonNull FiatMoney stringToFiatValueOrZero(@Nullable String string, @NonNull Currency currency) {
    try {
      if (string != null) return new FiatMoney(new BigDecimal(string), currency);
    } catch (NumberFormatException ignored) { }

    return new FiatMoney(BigDecimal.ZERO, currency);
  }

  private @NonNull Money.MobileCoin stringToMobileCoinValueOrZero(@Nullable String string) {
    try {
      if (string != null) return Money.mobileCoin(new BigDecimal(string));
    } catch (NumberFormatException ignored) { }

    return Money.MobileCoin.ZERO;
  }

  public @NonNull CreatePaymentDetails getCreatePaymentDetails() {
    CharSequence noteLocal = this.note.getValue();
    String       note      = noteLocal != null ? noteLocal.toString() : null;
    return new CreatePaymentDetails(payee, Objects.requireNonNull(inputState.getState().getMoney()), note);
  }

  private static @NonNull String updateAmountString(@NonNull Context context, @NonNull String oldAmount, @NonNull AmountKeyboardGlyph glyph, int maxPrecision) {
    if (glyph == AmountKeyboardGlyph.NONE) {
      return oldAmount;
    }

    if (glyph == AmountKeyboardGlyph.BACK) {
      if (!oldAmount.isEmpty()) {
        String newAmount = oldAmount.substring(0, oldAmount.length() - 1);
        if (newAmount.isEmpty()) {
          return AmountKeyboardGlyph.ZERO.getGlyph(context);
        } else {
          return newAmount;
        }
      }

      return oldAmount;
    }

    boolean oldAmountIsZero = AmountKeyboardGlyph.ZERO.getGlyph(context).equals(oldAmount);
    int     decimalIndex    = oldAmount.indexOf(AmountKeyboardGlyph.DECIMAL.getGlyph(context));

    if (glyph == AmountKeyboardGlyph.DECIMAL) {
      if (oldAmountIsZero) {
        return AmountKeyboardGlyph.ZERO.getGlyph(context) + glyph.getGlyph(context);
      } else if (decimalIndex > -1) {
        return oldAmount;
      }
    }

    if (decimalIndex > -1 && oldAmount.length() - 1 - decimalIndex >= maxPrecision) {
      return oldAmount;
    }

    if (oldAmountIsZero) {
      return glyph.getGlyph(context);
    } else {
      return oldAmount + glyph.getGlyph(context);
    }
  }

  public static final class Factory implements ViewModelProvider.Factory {

    private final PayeeParcelable payee;
    private final CharSequence    note;

    public Factory(@NonNull PayeeParcelable payee, @Nullable CharSequence note) {
      this.payee = payee;
      this.note  = note;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new CreatePaymentViewModel(payee, note);
    }
  }
}
