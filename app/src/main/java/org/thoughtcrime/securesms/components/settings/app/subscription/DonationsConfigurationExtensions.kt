package org.thoughtcrime.securesms.components.settings.app.subscription

import org.signal.core.util.money.FiatMoney
import org.signal.core.util.money.PlatformCurrencyUtil
import org.thoughtcrime.securesms.badges.Badges
import org.thoughtcrime.securesms.badges.models.Badge
import org.whispersystems.signalservice.internal.push.DonationsConfiguration
import org.whispersystems.signalservice.internal.push.DonationsConfiguration.BOOST_LEVEL
import org.whispersystems.signalservice.internal.push.DonationsConfiguration.GIFT_LEVEL
import org.whispersystems.signalservice.internal.push.DonationsConfiguration.LevelConfiguration
import org.whispersystems.signalservice.internal.push.DonationsConfiguration.SUBSCRIPTION_LEVELS
import java.math.BigDecimal
import java.util.Currency

private const val CARD = "CARD"
private const val PAYPAL = "PAYPAL"

/**
 * Transforms the DonationsConfiguration into a Set<FiatMoney> which has been properly filtered
 * for available currencies on the platform and based off user device availability.
 *
 * CARD   - Google Pay & Credit Card
 * PAYPAL - PayPal
 *
 * @param level                    The subscription level to get amounts for
 * @param paymentMethodAvailability Predicate object which checks whether different payment methods are availble.
 */
fun DonationsConfiguration.getSubscriptionAmounts(
  level: Int,
  paymentMethodAvailability: PaymentMethodAvailability = DefaultPaymentMethodAvailability
): Set<FiatMoney> {
  require(SUBSCRIPTION_LEVELS.contains(level))

  return getFilteredCurrencies(paymentMethodAvailability).map { (code, config) ->
    val amount: BigDecimal = config.subscription[level]!!
    FiatMoney(amount, Currency.getInstance(code.uppercase()))
  }.toSet()
}

/**
 * Currently, we only support a single gift badge at level GIFT_LEVEL
 */
fun DonationsConfiguration.getGiftBadges(): List<Badge> {
  val configuration = levels[GIFT_LEVEL]
  return listOfNotNull(configuration?.badge?.let { Badges.fromServiceBadge(it) })
}

/**
 * Currently, we only support a single gift badge amount per currency
 */
fun DonationsConfiguration.getGiftBadgeAmounts(paymentMethodAvailability: PaymentMethodAvailability = DefaultPaymentMethodAvailability): Map<Currency, FiatMoney> {
  return getFilteredCurrencies(paymentMethodAvailability).filter {
    it.value.oneTime[GIFT_LEVEL]?.isNotEmpty() == true
  }.mapKeys {
    Currency.getInstance(it.key.uppercase())
  }.mapValues {
    FiatMoney(it.value.oneTime[GIFT_LEVEL]!!.first(), it.key)
  }
}

/**
 * Currently, we only support a single boost badge at level BOOST_LEVEL
 */
fun DonationsConfiguration.getBoostBadges(): List<Badge> {
  val configuration = levels[BOOST_LEVEL]
  return listOfNotNull(configuration?.badge?.let { Badges.fromServiceBadge(it) })
}

fun DonationsConfiguration.getBoostAmounts(paymentMethodAvailability: PaymentMethodAvailability = DefaultPaymentMethodAvailability): Map<Currency, List<FiatMoney>> {
  return getFilteredCurrencies(paymentMethodAvailability).filter {
    it.value.oneTime[BOOST_LEVEL]?.isNotEmpty() == true
  }.mapKeys {
    Currency.getInstance(it.key.uppercase())
  }.mapValues { (currency, config) ->
    config.oneTime[BOOST_LEVEL]!!.map { FiatMoney(it, currency) }
  }
}

fun DonationsConfiguration.getBadge(level: Int): Badge {
  require(level == GIFT_LEVEL || level == BOOST_LEVEL || SUBSCRIPTION_LEVELS.contains(level))
  return Badges.fromServiceBadge(levels[level]!!.badge)
}

fun DonationsConfiguration.getSubscriptionLevels(): Map<Int, LevelConfiguration> {
  return levels.filterKeys { SUBSCRIPTION_LEVELS.contains(it) }.toSortedMap()
}

/**
 * Get a map describing the minimum donation amounts per currency.
 * This returns only the currencies available to the user.
 */
fun DonationsConfiguration.getMinimumDonationAmounts(paymentMethodAvailability: PaymentMethodAvailability = DefaultPaymentMethodAvailability): Map<Currency, FiatMoney> {
  return getFilteredCurrencies(paymentMethodAvailability)
    .mapKeys { Currency.getInstance(it.key.uppercase()) }
    .mapValues { FiatMoney(it.value.minimum, it.key) }
}

fun DonationsConfiguration.getAvailablePaymentMethods(currencyCode: String): Set<String> {
  return currencies[currencyCode.lowercase()]?.supportedPaymentMethods ?: emptySet()
}

private fun DonationsConfiguration.getFilteredCurrencies(paymentMethodAvailability: PaymentMethodAvailability): Map<String, DonationsConfiguration.CurrencyConfiguration> {
  val userPaymentMethods = paymentMethodAvailability.toSet()
  val availableCurrencyCodes = PlatformCurrencyUtil.getAvailableCurrencyCodes()
  return currencies.filter { (code, config) ->
    val areAllMethodsAvailable = config.supportedPaymentMethods.any { it in userPaymentMethods }
    availableCurrencyCodes.contains(code.uppercase()) && areAllMethodsAvailable
  }
}

/**
 * This interface is available to ease unit testing of the extension methods in
 * this file. In all normal situations, you can just allow the methods to use the
 * default value.
 */
interface PaymentMethodAvailability {
  fun isPayPalAvailable(): Boolean
  fun isGooglePayOrCreditCardAvailable(): Boolean

  fun toSet(): Set<String> {
    val set = mutableSetOf<String>()
    if (isPayPalAvailable()) {
      set.add(PAYPAL)
    }

    if (isGooglePayOrCreditCardAvailable()) {
      set.add(CARD)
    }

    return set
  }
}

private object DefaultPaymentMethodAvailability : PaymentMethodAvailability {
  override fun isPayPalAvailable(): Boolean = InAppDonations.isPayPalAvailable()
  override fun isGooglePayOrCreditCardAvailable(): Boolean = InAppDonations.isCreditCardAvailable() || InAppDonations.isGooglePayAvailable()
}
