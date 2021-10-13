package org.thoughtcrime.securesms.keyvalue

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.donations.StripeApi
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.payments.currency.CurrencyUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.Currency
import java.util.Locale

internal class DonationsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_CURRENCY_CODE = "donation.currency.code"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(KEY_CURRENCY_CODE)

  private val currencyPublisher: Subject<Currency> by lazy { BehaviorSubject.createDefault(getCurrency()) }
  val observableCurrency: Observable<Currency> by lazy { currencyPublisher }

  fun getCurrency(): Currency {
    val currencyCode = getString(KEY_CURRENCY_CODE, null)
    val currency: Currency? = if (currencyCode == null) {
      val localeCurrency = CurrencyUtil.getCurrencyByLocale(Locale.getDefault())
      if (localeCurrency == null) {
        val e164 = TextSecurePreferences.getLocalNumber(ApplicationDependencies.getApplication())
        if (e164 == null) {
          null
        } else {
          CurrencyUtil.getCurrencyByE164(e164)
        }
      } else {
        localeCurrency
      }
    } else {
      CurrencyUtil.getCurrencyByCurrencyCode(currencyCode)
    }

    return if (currency != null && StripeApi.Validation.supportedCurrencyCodes.contains(currency.currencyCode)) {
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
