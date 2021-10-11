package org.thoughtcrime.securesms.contacts.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ArgentinaFuzzyMatcherTest {

  @Test
  public void matches() {
    FuzzyPhoneNumberHelper.ArgentinaFuzzyMatcher subject = new FuzzyPhoneNumberHelper.ArgentinaFuzzyMatcher();

    assertTrue(subject.matches("+545512345678"));
    assertTrue(subject.matches("+5495512345678"));

    assertFalse(subject.matches("+54155123456"));
    assertFalse(subject.matches("+54551234567890"));
    assertFalse(subject.matches("+535512345678"));
    assertFalse(subject.matches("+5315512345678"));
  }

  @Test
  public void getVariant() {
    FuzzyPhoneNumberHelper.ArgentinaFuzzyMatcher subject = new FuzzyPhoneNumberHelper.ArgentinaFuzzyMatcher();

    assertEquals("+545512345678", subject.getVariant("+5495512345678"));
    assertEquals("+5495512345678", subject.getVariant("+545512345678"));

    assertNull(subject.getVariant(""));
    assertNull(subject.getVariant("+535512345678"));
  }

  @Test
  public void isPreferredVariant() {
    FuzzyPhoneNumberHelper.ArgentinaFuzzyMatcher subject = new FuzzyPhoneNumberHelper.ArgentinaFuzzyMatcher();

    assertTrue(subject.isPreferredVariant("+5495512345678"));

    assertFalse(subject.isPreferredVariant("+545512345678"));
    assertFalse(subject.isPreferredVariant("+54551234567"));
  }
}
