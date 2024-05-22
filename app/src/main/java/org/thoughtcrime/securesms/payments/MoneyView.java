package org.thoughtcrime.securesms.payments;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.util.DateUtils;
import org.whispersystems.signalservice.api.payments.Currency;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyView extends AppCompatTextView {
  private FormatterOptions formatterOptions;

  public MoneyView(@NonNull Context context) {
    super(context);

    init(context, null);
  }

  public MoneyView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    init(context, attrs);
  }

  public MoneyView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init(context, attrs);
  }

  public void init(@NonNull Context context, @Nullable AttributeSet attrs) {
    FormatterOptions.Builder builder = FormatterOptions.builder(Locale.getDefault());

    TypedArray styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.MoneyView, 0, 0);

    if (styledAttributes.getBoolean(R.styleable.MoneyView_always_show_sign, false)) {
      builder.alwaysPrefixWithSign();
    }

    formatterOptions = builder.withoutSpaceBeforeUnit().build();

    String value = styledAttributes.getString(R.styleable.MoneyView_money);
    if (value != null) {
      try {
        setMoney(Money.parse(value));
      } catch (Money.ParseException e) {
        throw new AssertionError("Invalid money format", e);
      }
    }

    styledAttributes.recycle();
  }

  public @NonNull String localizeAmountString(@NonNull String amount) {
    String decimalSeparator  = String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator());
    String groupingSeparator = String.valueOf(DecimalFormatSymbols.getInstance().getGroupingSeparator());

    return amount.replace(".", "__D__").replace(",", "__G__").replace("__D__", decimalSeparator).replace("__G__", groupingSeparator);
  }

  public void setMoney(@NonNull String amount, @NonNull Currency currency) {
    SpannableString balanceSpan   = new SpannableString(localizeAmountString(amount) + currency.getCurrencyCode());
    int             currencyIndex = balanceSpan.length() - currency.getCurrencyCode().length();
    balanceSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.payment_currency_code_foreground_color)), currencyIndex, currencyIndex + currency.getCurrencyCode().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    setText(balanceSpan);
  }

  public void setMoney(@NonNull Money money) {
    setMoney(money, true, 0L);
  }

  public void setMoney(@NonNull Money money, boolean highlightCurrency) {
    setMoney(money, highlightCurrency, 0L);
  }

  public void setMoney(@NonNull Money money, boolean highlightCurrency, long timestamp) {
    setMoney(money, timestamp, (highlightCurrency ? new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.payment_currency_code_foreground_color)) : null));
  }

  public void setMoney(@NonNull Money money, long timestamp, @Nullable Object currencySpan) {
    String          balance       = money.toString(formatterOptions);
    int             currencyIndex = balance.indexOf(money.getCurrency().getCurrencyCode());

    final SpannableString balanceSpan;

    if (timestamp > 0L) {
      balanceSpan = new SpannableString(getResources().getString(R.string.CurrencyAmountFormatter_s_at_s,
                                        balance,
                                        DateUtils.getTimeString(AppDependencies.getApplication(), Locale.getDefault(), timestamp)));
    } else {
      balanceSpan = new SpannableString(balance);
    }

    if (currencySpan != null) {
      balanceSpan.setSpan(currencySpan, currencyIndex, currencyIndex + money.getCurrency().getCurrencyCode().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    setText(balanceSpan);
  }

  private static @NonNull NumberFormat getMoneyFormat(int decimalPrecision) {
    NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());

    numberFormat.setGroupingUsed(true);
    numberFormat.setMaximumFractionDigits(decimalPrecision);

    return numberFormat;
  }
}
