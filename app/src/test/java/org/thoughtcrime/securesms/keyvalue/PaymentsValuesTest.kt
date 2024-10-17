package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PaymentsValuesTest {

  private lateinit var paymentValues: PaymentsValues

  @Before
  fun setup() {
    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }

    mockkObject(RemoteConfig)
    mockkObject(SignalStore)

    paymentValues = mockk()
    every { paymentValues.paymentsAvailability } answers { callOriginal() }

    every { SignalStore.payments } returns paymentValues

    every { SignalStore.account.isRegistered } returns true
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `when unregistered, expect NOT_IN_REGION`() {
    every { SignalStore.account.isRegistered } returns false

    assertEquals(PaymentsAvailability.NOT_IN_REGION, SignalStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag disabled and no account, expect DISABLED_REMOTELY`() {
    every { SignalStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns false
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.DISABLED_REMOTELY, SignalStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag disabled but has account, expect WITHDRAW_ONLY`() {
    every { SignalStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns false
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, SignalStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account, expect REGISTRATION_AVAILABLE`() {
    every { SignalStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.REGISTRATION_AVAILABLE, SignalStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account, expect WITHDRAW_AND_SEND`() {
    every { SignalStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_AND_SEND, SignalStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account and in the country blocklist, expect NOT_IN_REGION`() {
    every { SignalStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns false
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.NOT_IN_REGION, SignalStore.payments.paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account and in the country blocklist, expect WITHDRAW_ONLY`() {
    every { SignalStore.account.e164 } returns "+15551234567"
    every { paymentValues.mobileCoinPaymentsEnabled() } returns true
    every { RemoteConfig.payments } returns true
    every { RemoteConfig.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, SignalStore.payments.paymentsAvailability)
  }
}
