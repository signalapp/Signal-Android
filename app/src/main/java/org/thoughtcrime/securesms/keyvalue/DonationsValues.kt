package org.thoughtcrime.securesms.keyvalue

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.donations.StripeApi
import java.util.Currency
import java.util.Locale

internal class DonationsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_CURRENCY_CODE = "donation.currency.code"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(KEY_CURRENCY_CODE)

  private val currencyPublisher: Subject<Currency> = BehaviorSubject.createDefault(getCurrency())
  val observableCurrency: Observable<Currency> = currencyPublisher

  fun getCurrency(): Currency {
    val currencyCode = getString(KEY_CURRENCY_CODE, null)
    val currency = if (currencyCode == null) {
      Currency.getInstance(Locale.getDefault())
    } else {
      Currency.getInstance(currencyCode)
    }

    return if (StripeApi.Validation.supportedCurrencyCodes.contains(currency.currencyCode)) {
      currency
    } else {
      Currency.getInstance("USD")
    }
  }

  fun setCurrency(currency: Currency) {
    putString(KEY_CURRENCY_CODE, currency.currencyCode)
    currencyPublisher.onNext(currency)
  }
}
