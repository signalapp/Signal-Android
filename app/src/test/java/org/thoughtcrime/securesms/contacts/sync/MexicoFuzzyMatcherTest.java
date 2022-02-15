package org.thoughtcrime.securesms.contacts.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MexicoFuzzyMatcherTest {

  @Test
  public void matches() {
    FuzzyPhoneNumberHelper.MexicoFuzzyMatcher subject = new FuzzyPhoneNumberHelper.MexicoFuzzyMatcher();

    assertTrue(subject.matches("+525512345678"));
    assertTrue(subject.matches("+5215512345678"));

    assertFalse(subject.matches("+52155123456"));
    assertFalse(subject.matches("+52551234567890"));
    assertFalse(subject.matches("+535512345678"));
    assertFalse(subject.matches("+5315512345678"));
  }

  @Test
  public void getVariant() {
    FuzzyPhoneNumberHelper.MexicoFuzzyMatcher subject = new FuzzyPhoneNumberHelper.MexicoFuzzyMatcher();

    assertEquals("+525512345678", subject.getVariant("+5215512345678"));
    assertEquals("+5215512345678", subject.getVariant("+525512345678"));

    assertNull(subject.getVariant(""));
    assertNull(subject.getVariant("+535512345678"));
  }

  @Test
  public void isPreferredVariant() {
    FuzzyPhoneNumberHelper.MexicoFuzzyMatcher subject = new FuzzyPhoneNumberHelper.MexicoFuzzyMatcher();

    assertTrue(subject.isPreferredVariant("+525512345678"));

    assertFalse(subject.isPreferredVariant("+5215512345678"));
    assertFalse(subject.isPreferredVariant("+52551234567"));
  }
}
