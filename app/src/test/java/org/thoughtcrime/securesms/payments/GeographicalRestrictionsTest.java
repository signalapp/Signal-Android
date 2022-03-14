package org.thoughtcrime.securesms.payments;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.thoughtcrime.securesms.util.FeatureFlags;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public final class GeographicalRestrictionsTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<FeatureFlags> featureFlagsMockedStatic;

  @Test
  public void e164Allowed_general() {
    when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("");
    assertTrue(GeographicalRestrictions.e164Allowed("+15551234567"));

    when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("1");
    assertFalse(GeographicalRestrictions.e164Allowed("+15551234567"));

    when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("1,44");
    assertFalse(GeographicalRestrictions.e164Allowed("+15551234567"));
    assertFalse(GeographicalRestrictions.e164Allowed("+445551234567"));
    assertTrue(GeographicalRestrictions.e164Allowed("+525551234567"));

    when(FeatureFlags.paymentsCountryBlocklist()).thenReturn("1 234,44");
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
