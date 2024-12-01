package org.thoughtcrime.securesms.payments

import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.util.RemoteConfig

class GeographicalRestrictionsTest {
  @Test
  fun e164Allowed_general() {
    mockkStatic(RemoteConfig::class) {
      every { RemoteConfig.paymentsCountryBlocklist } returns ""
      assertTrue(GeographicalRestrictions.e164Allowed("+15551234567"))

      every { RemoteConfig.paymentsCountryBlocklist } returns "1"
      assertFalse(GeographicalRestrictions.e164Allowed("+15551234567"))

      every { RemoteConfig.paymentsCountryBlocklist } returns "1,44"
      assertFalse(GeographicalRestrictions.e164Allowed("+15551234567"))
      assertFalse(GeographicalRestrictions.e164Allowed("+445551234567"))
      assertTrue(GeographicalRestrictions.e164Allowed("+525551234567"))

      every { RemoteConfig.paymentsCountryBlocklist } returns "1 234,44"
      assertFalse(GeographicalRestrictions.e164Allowed("+12341234567"))
      assertTrue(GeographicalRestrictions.e164Allowed("+15551234567"))
      assertTrue(GeographicalRestrictions.e164Allowed("+525551234567"))
      assertTrue(GeographicalRestrictions.e164Allowed("+2345551234567"))
    }
  }

  @Test
  fun e164Allowed_nullNotAllowed() {
    assertFalse(GeographicalRestrictions.e164Allowed(null))
  }
}
