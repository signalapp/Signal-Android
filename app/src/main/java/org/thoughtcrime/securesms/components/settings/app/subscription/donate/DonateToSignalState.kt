package org.thoughtcrime.securesms.components.settings.app.subscription.donate

import org.signal.core.util.money.FiatMoney
import org.signal.donations.InAppPaymentType
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppDonations
import org.thoughtcrime.securesms.components.settings.app.subscription.boost.Boost
import org.thoughtcrime.securesms.components.settings.app.subscription.manage.NonVerifiedMonthlyDonation
import org.thoughtcrime.securesms.database.model.databaseprotos.PendingOneTimeDonation
import org.thoughtcrime.securesms.database.model.isLongRunning
import org.thoughtcrime.securesms.database.model.isPending
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.subscription.Subscription
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.TimeUnit

data class DonateToSignalState(
  val inAppPaymentType: InAppPaymentType,
  val oneTimeDonationState: OneTimeDonationState = OneTimeDonationState(),
  val monthlyDonationState: MonthlyDonationState = MonthlyDonationState()
) {

  val areFieldsEnabled: Boolean
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> oneTimeDonationState.donationStage == DonationStage.READY
      InAppPaymentType.RECURRING_DONATION -> monthlyDonationState.donationStage == DonationStage.READY
      else -> error("This flow does not support $inAppPaymentType")
    }

  val badge: Badge?
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> oneTimeDonationState.badge
      InAppPaymentType.RECURRING_DONATION -> monthlyDonationState.selectedSubscription?.badge
      else -> error("This flow does not support $inAppPaymentType")
    }

  val canSetCurrency: Boolean
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> areFieldsEnabled && !oneTimeDonationState.isOneTimeDonationPending
      InAppPaymentType.RECURRING_DONATION -> areFieldsEnabled && !monthlyDonationState.isSubscriptionActive
      else -> error("This flow does not support $inAppPaymentType")
    }

  val selectedCurrency: Currency
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> oneTimeDonationState.selectedCurrency
      InAppPaymentType.RECURRING_DONATION -> monthlyDonationState.selectedCurrency
      else -> error("This flow does not support $inAppPaymentType")
    }

  val selectableCurrencyCodes: List<String>
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> oneTimeDonationState.selectableCurrencyCodes
      InAppPaymentType.RECURRING_DONATION -> monthlyDonationState.selectableCurrencyCodes
      else -> error("This flow does not support $inAppPaymentType")
    }

  val level: Int
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> 1
      InAppPaymentType.RECURRING_DONATION -> monthlyDonationState.selectedSubscription!!.level
      else -> error("This flow does not support $inAppPaymentType")
    }

  val continueEnabled: Boolean
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> areFieldsEnabled && oneTimeDonationState.isSelectionValid && InAppDonations.hasAtLeastOnePaymentMethodAvailable()
      InAppPaymentType.RECURRING_DONATION -> areFieldsEnabled && monthlyDonationState.isSelectionValid && InAppDonations.hasAtLeastOnePaymentMethodAvailable()
      else -> error("This flow does not support $inAppPaymentType")
    }

  val canContinue: Boolean
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> continueEnabled && !oneTimeDonationState.isOneTimeDonationPending
      InAppPaymentType.RECURRING_DONATION -> continueEnabled && !monthlyDonationState.isSubscriptionActive && !monthlyDonationState.transactionState.isInProgress
      else -> error("This flow does not support $inAppPaymentType")
    }

  val canUpdate: Boolean
    get() = when (inAppPaymentType) {
      InAppPaymentType.ONE_TIME_DONATION -> false
      InAppPaymentType.RECURRING_DONATION -> areFieldsEnabled && monthlyDonationState.isSelectionValid && monthlyDonationState.isSubscriptionActive && !monthlyDonationState.transactionState.isInProgress
      else -> error("This flow does not support $inAppPaymentType")
    }

  val isUpdateLongRunning: Boolean
    get() = monthlyDonationState.activeSubscription?.paymentMethod == ActiveSubscription.PaymentMethod.SEPA_DEBIT

  data class OneTimeDonationState(
    val badge: Badge? = null,
    val selectedCurrency: Currency = SignalStore.inAppPayments.getOneTimeCurrency(),
    val boosts: List<Boost> = emptyList(),
    val selectedBoost: Boost? = null,
    val customAmount: FiatMoney = FiatMoney(BigDecimal.ZERO, selectedCurrency),
    val isCustomAmountFocused: Boolean = false,
    val donationStage: DonationStage = DonationStage.INIT,
    val selectableCurrencyCodes: List<String> = emptyList(),
    private val pendingOneTimeDonation: PendingOneTimeDonation? = null,
    private val minimumDonationAmounts: Map<Currency, FiatMoney> = emptyMap()
  ) {
    val isOneTimeDonationPending: Boolean = pendingOneTimeDonation.isPending()
    val isOneTimeDonationLongRunning: Boolean = pendingOneTimeDonation.isLongRunning()
    val isNonVerifiedIdeal = pendingOneTimeDonation?.pendingVerification == true

    val minimumDonationAmountOfSelectedCurrency: FiatMoney = minimumDonationAmounts[selectedCurrency] ?: FiatMoney(BigDecimal.ZERO, selectedCurrency)
    private val isCustomAmountTooSmall: Boolean = if (isCustomAmountFocused) customAmount.amount < minimumDonationAmountOfSelectedCurrency.amount else false
    private val isCustomAmountZero: Boolean = customAmount.amount == BigDecimal.ZERO

    val isSelectionValid: Boolean = if (isCustomAmountFocused) !isCustomAmountTooSmall else selectedBoost != null
    val shouldDisplayCustomAmountTooSmallError: Boolean = isCustomAmountTooSmall && !isCustomAmountZero
  }

  data class MonthlyDonationState(
    val selectedCurrency: Currency = SignalStore.inAppPayments.getRecurringDonationCurrency(),
    val subscriptions: List<Subscription> = emptyList(),
    private val _activeSubscription: ActiveSubscription? = null,
    val selectedSubscription: Subscription? = null,
    val donationStage: DonationStage = DonationStage.INIT,
    val selectableCurrencyCodes: List<String> = emptyList(),
    val nonVerifiedMonthlyDonation: NonVerifiedMonthlyDonation? = null,
    val transactionState: TransactionState = TransactionState()
  ) {
    val isSubscriptionActive: Boolean = _activeSubscription?.isActive == true
    val isSubscriptionInProgress: Boolean = _activeSubscription?.isInProgress == true
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
