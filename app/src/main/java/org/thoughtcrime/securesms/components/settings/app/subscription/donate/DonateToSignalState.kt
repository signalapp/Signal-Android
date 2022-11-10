package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.TimeUnit

data class DonateToSignalState(
  val donateToSignalType: DonateToSignalType,
  val oneTimeDonationState: OneTimeDonationState = OneTimeDonationState(),
  val monthlyDonationState: MonthlyDonationState = MonthlyDonationState()
) {

  val areFieldsEnabled: Boolean
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> oneTimeDonationState.donationStage == DonationStage.READY
      DonateToSignalType.MONTHLY -> monthlyDonationState.donationStage == DonationStage.READY && !monthlyDonationState.transactionState.isInProgress
    }

  val badge: Badge?
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> oneTimeDonationState.badge
      DonateToSignalType.MONTHLY -> monthlyDonationState.selectedSubscription?.badge
    }

  val canSetCurrency: Boolean
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> areFieldsEnabled
      DonateToSignalType.MONTHLY -> areFieldsEnabled && !monthlyDonationState.isSubscriptionActive
    }

  val selectedCurrency: Currency
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> oneTimeDonationState.selectedCurrency
      DonateToSignalType.MONTHLY -> monthlyDonationState.selectedCurrency
    }

  val selectableCurrencyCodes: List<String>
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> oneTimeDonationState.selectableCurrencyCodes
      DonateToSignalType.MONTHLY -> monthlyDonationState.selectableCurrencyCodes
    }

  val level: Int
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> 1
      DonateToSignalType.MONTHLY -> monthlyDonationState.selectedSubscription!!.level
    }

  val canContinue: Boolean
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> areFieldsEnabled && oneTimeDonationState.isSelectionValid && InAppDonations.hasAtLeastOnePaymentMethodAvailable()
      DonateToSignalType.MONTHLY -> areFieldsEnabled && monthlyDonationState.isSelectionValid && InAppDonations.hasAtLeastOnePaymentMethodAvailable()
    }

  val canUpdate: Boolean
    get() = when (donateToSignalType) {
      DonateToSignalType.ONE_TIME -> false
      DonateToSignalType.MONTHLY -> areFieldsEnabled && monthlyDonationState.isSelectionValid
    }

  data class OneTimeDonationState(
    val badge: Badge? = null,
    val selectedCurrency: Currency = SignalStore.donationsValues().getOneTimeCurrency(),
    val boosts: List<Boost> = emptyList(),
    val selectedBoost: Boost? = null,
    val customAmount: FiatMoney = FiatMoney(BigDecimal.ZERO, selectedCurrency),
    val isCustomAmountFocused: Boolean = false,
    val donationStage: DonationStage = DonationStage.INIT,
    val selectableCurrencyCodes: List<String> = emptyList()
  ) {
    val isSelectionValid: Boolean = if (isCustomAmountFocused) customAmount.amount > BigDecimal.ZERO else selectedBoost != null
  }

  data class MonthlyDonationState(
    val selectedCurrency: Currency = SignalStore.donationsValues().getSubscriptionCurrency(),
    val subscriptions: List<Subscription> = emptyList(),
    private val _activeSubscription: ActiveSubscription? = null,
    val selectedSubscription: Subscription? = null,
    val donationStage: DonationStage = DonationStage.INIT,
    val selectableCurrencyCodes: List<String> = emptyList(),
    val transactionState: TransactionState = TransactionState()
  ) {
    val isSubscriptionActive: Boolean = _activeSubscription?.isActive == true
    val activeLevel: Int? = _activeSubscription?.activeSubscription?.level
    val activeSubscription: ActiveSubscription.Subscription? = _activeSubscription?.activeSubscription
    val isActiveSubscriptionEnding: Boolean = _activeSubscription?.isActive == true && _activeSubscription.activeSubscription.willCancelAtPeriodEnd()
    val renewalTimestamp = TimeUnit.SECONDS.toMillis(activeSubscription?.endOfCurrentPeriod ?: 0L)
    val isSelectionValid = selectedSubscription != null && (!isSubscriptionActive || selectedSubscription.level != activeSubscription?.level)
  }

  enum class DonationStage {
    INIT,
    READY,
    FAILURE
  }

  data class TransactionState(
    val isTransactionJobPending: Boolean = false,
    val isLevelUpdateInProgress: Boolean = false
  ) {
    val isInProgress: Boolean = isTransactionJobPending || isLevelUpdateInProgress
  }
}
