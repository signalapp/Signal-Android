package org.thoughtcrime.securesms.components.settings.app.subscription

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.whispersystems.signalservice.internal.push.DonationsConfiguration
import org.whispersystems.signalservice.internal.util.JsonUtil
import java.util.Currency

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DonationsConfigurationExtensionsKtTest {

  private val testData: String = javaClass.classLoader!!.getResourceAsStream("donations_configuration_test_data.json").bufferedReader().readText()
  private val testSubject = JsonUtil.fromJson(testData, DonationsConfiguration::class.java)

  @Test
  fun `Given all methods are available, when I getSubscriptionAmounts, then I expect BIF`() {
    val subscriptionPrices = testSubject.getSubscriptionAmounts(DonationsConfiguration.SUBSCRIPTION_LEVELS.first(), AllPaymentMethodsAvailability)

    assertEquals(1, subscriptionPrices.size)
    assertEquals("BIF", subscriptionPrices.first().currency.currencyCode)
  }

  @Test
  fun `Given only PayPal available, when I getSubscriptionAmounts, then I expect BIF and JPY`() {
    val subscriptionPrices = testSubject.getSubscriptionAmounts(DonationsConfiguration.SUBSCRIPTION_LEVELS.first(), PayPalOnly)

    assertEquals(2, subscriptionPrices.size)
    assertTrue(subscriptionPrices.map { it.currency.currencyCode }.containsAll(setOf("JPY", "BIF")))
  }

  @Test
  fun `Given only Card available, when I getSubscriptionAmounts, then I expect BIF and USD`() {
    val subscriptionPrices = testSubject.getSubscriptionAmounts(DonationsConfiguration.SUBSCRIPTION_LEVELS.first(), CardOnly)

    assertEquals(2, subscriptionPrices.size)
    assertTrue(subscriptionPrices.map { it.currency.currencyCode }.containsAll(setOf("USD", "BIF")))
  }

  @Test
  fun `When I getGiftBadges, then I expect exactly 1 badge with the id GIFT`() {
    mockkStatic(ApplicationDependencies::class) {
      every { ApplicationDependencies.getApplication() } returns ApplicationProvider.getApplicationContext()

      val giftBadges = testSubject.getGiftBadges()

      assertEquals(1, giftBadges.size)
      assertTrue(giftBadges.first().isGift())
    }
  }

  @Test
  fun `When I getBoostBadges, then I expect exactly 1 badge with the id BOOST`() {
    mockkStatic(ApplicationDependencies::class) {
      every { ApplicationDependencies.getApplication() } returns ApplicationProvider.getApplicationContext()

      val boostBadges = testSubject.getBoostBadges()

      assertEquals(1, boostBadges.size)
      assertTrue(boostBadges.first().isBoost())
    }
  }

  @Test
  fun `When I getSubscriptionLevels, then I expect the exact 3 defined subscription levels`() {
    val subscriptionLevels = testSubject.getSubscriptionLevels()

    assertEquals(3, subscriptionLevels.size)
    assertEquals(DonationsConfiguration.SUBSCRIPTION_LEVELS, subscriptionLevels.keys)
    subscriptionLevels.keys.fold(0) { acc, i ->
      assertTrue(acc < i)
      i
    }
  }

  @Test
  fun `Given all methods are available, when I getGiftAmounts, then I expect BIF`() {
    val giftAmounts = testSubject.getGiftBadgeAmounts(AllPaymentMethodsAvailability)

    assertEquals(1, giftAmounts.size)
    assertNotNull(giftAmounts[Currency.getInstance("BIF")])
  }

  @Test
  fun `Given only PayPal available, when I getGiftAmounts, then I expect BIF and JPY`() {
    val giftAmounts = testSubject.getGiftBadgeAmounts(PayPalOnly)

    assertEquals(2, giftAmounts.size)
    assertTrue(giftAmounts.map { it.key.currencyCode }.containsAll(setOf("JPY", "BIF")))
  }

  @Test
  fun `Given only Card available, when I getGiftAmounts, then I expect BIF and USD`() {
    val giftAmounts = testSubject.getGiftBadgeAmounts(CardOnly)

    assertEquals(2, giftAmounts.size)
    assertTrue(giftAmounts.map { it.key.currencyCode }.containsAll(setOf("USD", "BIF")))
  }

  @Test
  fun `Given all methods are available, when I getBoostAmounts, then I expect BIF`() {
    val boostAmounts = testSubject.getBoostAmounts(AllPaymentMethodsAvailability)

    assertEquals(1, boostAmounts.size)
    assertNotNull(boostAmounts[Currency.getInstance("BIF")])
  }

  @Test
  fun `Given only PayPal available, when I getBoostAmounts, then I expect BIF and JPY`() {
    val boostAmounts = testSubject.getBoostAmounts(PayPalOnly)

    assertEquals(2, boostAmounts.size)
    assertTrue(boostAmounts.map { it.key.currencyCode }.containsAll(setOf("JPY", "BIF")))
  }

  @Test
  fun `Given only Card available, when I getBoostAmounts, then I expect BIF and USD`() {
    val boostAmounts = testSubject.getBoostAmounts(CardOnly)

    assertEquals(2, boostAmounts.size)
    assertTrue(boostAmounts.map { it.key.currencyCode }.containsAll(setOf("USD", "BIF")))
  }

  @Test
  fun `Given all methods are available, when I getMinimumDonationAmounts, then I expect BIF`() {
    val minimumDonationAmounts = testSubject.getMinimumDonationAmounts(AllPaymentMethodsAvailability)

    assertEquals(1, minimumDonationAmounts.size)
    assertNotNull(minimumDonationAmounts[Currency.getInstance("BIF")])
  }

  @Test
  fun `Given only PayPal available, when I getMinimumDonationAmounts, then I expect BIF and JPY`() {
    val minimumDonationAmounts = testSubject.getMinimumDonationAmounts(PayPalOnly)

    assertEquals(2, minimumDonationAmounts.size)
    assertTrue(minimumDonationAmounts.map { it.key.currencyCode }.containsAll(setOf("JPY", "BIF")))
  }

  @Test
  fun `Given only Card available, when I getMinimumDonationAmounts, then I expect BIF and USD`() {
    val minimumDonationAmounts = testSubject.getMinimumDonationAmounts(CardOnly)

    assertEquals(2, minimumDonationAmounts.size)
    assertTrue(minimumDonationAmounts.map { it.key.currencyCode }.containsAll(setOf("USD", "BIF")))
  }

  @Test
  fun `Given GIFT_LEVEL, When I getBadge, then I expect the gift badge`() {
    mockkStatic(ApplicationDependencies::class) {
      every { ApplicationDependencies.getApplication() } returns ApplicationProvider.getApplicationContext()
      val badge = testSubject.getBadge(DonationsConfiguration.GIFT_LEVEL)

      assertTrue(badge.isGift())
    }
  }

  @Test
  fun `Given BOOST_LEVEL, When I getBadge, then I expect the boost badge`() {
    mockkStatic(ApplicationDependencies::class) {
      every { ApplicationDependencies.getApplication() } returns ApplicationProvider.getApplicationContext()
      val badge = testSubject.getBadge(DonationsConfiguration.BOOST_LEVEL)

      assertTrue(badge.isBoost())
    }
  }

  @Test
  fun `Given a sub level, When I getBadge, then I expect a sub badge`() {
    mockkStatic(ApplicationDependencies::class) {
      every { ApplicationDependencies.getApplication() } returns ApplicationProvider.getApplicationContext()
      val badge = testSubject.getBadge(DonationsConfiguration.SUBSCRIPTION_LEVELS.first())

      assertTrue(badge.isSubscription())
    }
  }

  private object AllPaymentMethodsAvailability : PaymentMethodAvailability {
    override fun isPayPalAvailable(): Boolean = true
    override fun isGooglePayOrCreditCardAvailable(): Boolean = true
  }

  private object PayPalOnly : PaymentMethodAvailability {
    override fun isPayPalAvailable(): Boolean = true
    override fun isGooglePayOrCreditCardAvailable(): Boolean = false
  }

  private object CardOnly : PaymentMethodAvailability {
    override fun isPayPalAvailable(): Boolean = false
    override fun isGooglePayOrCreditCardAvailable(): Boolean = true
  }
}
