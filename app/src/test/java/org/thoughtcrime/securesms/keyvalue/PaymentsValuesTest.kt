package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.util.FeatureFlags

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class PaymentsValuesTest {

  @Before
  fun setup() {
    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }

    mockkObject(FeatureFlags)
  }

  @Test
  fun `when unregistered, expect NOT_IN_REGION`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, false)
      }
    )

    assertEquals(PaymentsAvailability.NOT_IN_REGION, SignalStore.paymentsValues().paymentsAvailability)
  }

  @Test
  fun `when flag disabled and no account, expect DISABLED_REMOTELY`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, true)
        putString(AccountValues.KEY_E164, "+15551234567")
        putBoolean(PaymentsValues.MOB_PAYMENTS_ENABLED, false)
      }
    )

    every { FeatureFlags.payments } returns false
    every { FeatureFlags.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.DISABLED_REMOTELY, SignalStore.paymentsValues().paymentsAvailability)
  }

  @Test
  fun `when flag disabled but has account, expect WITHDRAW_ONLY`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, true)
        putString(AccountValues.KEY_E164, "+15551234567")
        putBoolean(PaymentsValues.MOB_PAYMENTS_ENABLED, true)
      }
    )

    every { FeatureFlags.payments } returns false
    every { FeatureFlags.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, SignalStore.paymentsValues().paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account, expect REGISTRATION_AVAILABLE`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, true)
        putString(AccountValues.KEY_E164, "+15551234567")
        putBoolean(PaymentsValues.MOB_PAYMENTS_ENABLED, false)
      }
    )

    every { FeatureFlags.payments } returns true
    every { FeatureFlags.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.REGISTRATION_AVAILABLE, SignalStore.paymentsValues().paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account, expect WITHDRAW_AND_SEND`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, true)
        putString(AccountValues.KEY_E164, "+15551234567")
        putBoolean(PaymentsValues.MOB_PAYMENTS_ENABLED, true)
      }
    )

    every { FeatureFlags.payments } returns true
    every { FeatureFlags.paymentsCountryBlocklist } returns ""

    assertEquals(PaymentsAvailability.WITHDRAW_AND_SEND, SignalStore.paymentsValues().paymentsAvailability)
  }

  @Test
  fun `when flag enabled and no account and in the country blocklist, expect NOT_IN_REGION`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, true)
        putString(AccountValues.KEY_E164, "+15551234567")
        putBoolean(PaymentsValues.MOB_PAYMENTS_ENABLED, false)
      }
    )

    every { FeatureFlags.payments } returns true
    every { FeatureFlags.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.NOT_IN_REGION, SignalStore.paymentsValues().paymentsAvailability)
  }

  @Test
  fun `when flag enabled and has account and in the country blocklist, expect WITHDRAW_ONLY`() {
    setupStore(
      KeyValueDataSet().apply {
        putBoolean(AccountValues.KEY_IS_REGISTERED, true)
        putString(AccountValues.KEY_E164, "+15551234567")
        putBoolean(PaymentsValues.MOB_PAYMENTS_ENABLED, true)
      }
    )

    every { FeatureFlags.payments } returns true
    every { FeatureFlags.paymentsCountryBlocklist } returns "1"

    assertEquals(PaymentsAvailability.WITHDRAW_ONLY, SignalStore.paymentsValues().paymentsAvailability)
  }

  /**
   * Account values will overwrite some values upon first access, so this takes care of that
   */
  private fun setupStore(dataset: KeyValueDataSet) {
    val store = KeyValueStore(
      MockKeyValuePersistentStorage.withDataSet(
        dataset.apply {
          putString(AccountValues.KEY_ACI, "")
        }
      )
    )
    SignalStore.inject(store)
  }
}
