package org.thoughtcrime.securesms.components.settings.app.subscription.currency

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.livedata.Store
import java.util.Currency
import java.util.Locale

class SetCurrencyViewModel : ViewModel() {

  private val store = Store(SetCurrencyState())

  val state: LiveData<SetCurrencyState> = store.stateLiveData

  init {
    val defaultCurrency = SignalStore.donationsValues().getCurrency()

    store.update { state ->
      val platformCurrencies = Currency.getAvailableCurrencies()
      val stripeCurrencies = platformCurrencies
        .filter { StripeApi.Validation.supportedCurrencyCodes.contains(it.currencyCode) }
        .sortedWith(CurrencyComparator(BuildConfig.DEFAULT_CURRENCIES.split(",")))

      state.copy(
        selectedCurrencyCode = defaultCurrency.currencyCode,
        currencies = stripeCurrencies
      )
    }
  }

  fun setSelectedCurrency(selectedCurrencyCode: String) {
    store.update { it.copy(selectedCurrencyCode = selectedCurrencyCode) }
    SignalStore.donationsValues().setCurrency(Currency.getInstance(selectedCurrencyCode))
  }

  @VisibleForTesting
  class CurrencyComparator(private val defaults: List<String>) : Comparator<Currency> {

    companion object {
      private const val USD = "USD"
    }

    override fun compare(o1: Currency, o2: Currency): Int {
      val isO1Default = o1.currencyCode in defaults
      val isO2Default = o2.currencyCode in defaults

      return if (o1.currencyCode == o2.currencyCode) {
        0
      } else if (o1.currencyCode == USD) {
        -1
      } else if (o2.currencyCode == USD) {
        1
      } else if (isO1Default && isO2Default) {
        o1.getDisplayName(Locale.getDefault()).compareTo(o2.getDisplayName(Locale.getDefault()))
      } else if (isO1Default) {
        -1
      } else if (isO2Default) {
        1
      } else {
        o1.getDisplayName(Locale.getDefault()).compareTo(o2.getDisplayName(Locale.getDefault()))
      }
    }
  }
}
