package org.thoughtcrime.securesms.payments;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.testutil.EmptyLogger;
import org.thoughtcrime.securesms.util.FeatureFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest(FeatureFlags.class)
public final class GeographicalRestrictionsTest {

  @Before
  public void setup() {
    Log.initialize(new EmptyLogger());
    PowerMockito.mockStatic(FeatureFlags.class);
  }

  @Test
  public void e164Allowed_general() {
    PowerMockito.when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("");
    assertTrue(GeographicalRestrictions.e164Allowed("+15551234567"));

    PowerMockito.when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("1");
    assertFalse(GeographicalRestrictions.e164Allowed("+15551234567"));

    PowerMockito.when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("1,44");
    assertFalse(GeographicalRestrictions.e164Allowed("+15551234567"));
    assertFalse(GeographicalRestrictions.e164Allowed("+445551234567"));
    assertTrue(GeographicalRestrictions.e164Allowed("+525551234567"));

    PowerMockito.when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("1 234,44");
    assertFalse(GeographicalRestrictions.e164Allowed("+12341234567"));
    assertTrue(GeographicalRestrictions.e164Allowed("+15551234567"));
    assertTrue(GeographicalRestrictions.e164Allowed("+525551234567"));
    assertTrue(GeographicalRestrictions.e164Allowed("+2345551234567"));
  }

  @Test
  public void e164Allowed_nullNotAllowed() {
    assertFalse(GeographicalRestrictions.e164Allowed(null));
  }
}
