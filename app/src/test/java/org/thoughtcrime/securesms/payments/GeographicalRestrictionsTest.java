package org.thoughtcrime.securesms.payments;

import org.junit.Before;
import org.junit.Test;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.testutil.EmptyLogger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public final class GeographicalRestrictionsTest {

  private static final String INVALID_BLACKLIST = "asdfkljsdhfla";

  @Before
  public void setup() {
    Log.initialize(new EmptyLogger());
  }

  @Test
  public void bad_number_not_allowed() {
    assertFalse(GeographicalRestrictions.e164Allowed("bad_number", null));
  }

  @Test
  public void null_not_allowed() {
    assertFalse(GeographicalRestrictions.e164Allowed(null, null));
  }

  @Test
  public void uk_allowed() {
    assertTrue(GeographicalRestrictions.e164Allowed("+441617151234", null));
  }

  @Test
  public void us_not_allowed_in_release() {
    assumeFalse(BuildConfig.DEBUG);
    assertFalse(GeographicalRestrictions.e164Allowed("+15407011234", null));
  }

  @Test
  public void givenAnInvalidBlackList_whenIE164AllowedAUkNumber_thenIExpectTrue() {
    assertTrue(GeographicalRestrictions.e164Allowed("+441617151234", INVALID_BLACKLIST));
  }

  @Test
  public void givenAValidBlacklistWithRegionBlock_whenIE164AllowedANumberInThatRegion_thenIExpectFalse() {
    assertFalse(GeographicalRestrictions.e164Allowed("+73652222222", "7 365"));
  }

  @Test
  public void givenAValidBlacklistWithInvalidRegionBlock_whenIE164AllowedANumberInThatRegion_thenIExpectTrue() {
    assertTrue(GeographicalRestrictions.e164Allowed("+73652222222", "4,7 365 2"));
  }

  @Test
  public void givenAValidBlacklist_whenIE164AllowedANumberNotInTheBlacklist_thenIExpectTrue() {
    assertTrue(GeographicalRestrictions.e164Allowed("+73632222222", "4,7 365,44,33"));
  }

  @Test
  public void givenAValidBlacklist_whenIE164AllowedANumberInTheBlacklist_thenIExpectFalse() {
    assertFalse(GeographicalRestrictions.e164Allowed("+73632222222", "4,7,44,33"));
  }

  @Test
  public void givenAValidBlacklistWithExtraSpaces_whenIE164AllowedANumberInTheBlacklist_thenIExpectFalse() {
    assertFalse(GeographicalRestrictions.e164Allowed("+73632222222", " 4, 7, 44, 33 "));
  }

  @Test
  public void givenAValidBlacklistWithAreaCode_whenIE164AllowedANumberInTheBlacklistAreaCode_thenIExpectFalse() {
    assertFalse(GeographicalRestrictions.e164Allowed("+15065550199", "1 506"));
  }
}
