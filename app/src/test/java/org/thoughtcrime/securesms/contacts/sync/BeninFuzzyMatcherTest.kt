package org.thoughtcrime.securesms.contacts.sync

import org.junit.Assert
import org.junit.Test
import org.thoughtcrime.securesms.contacts.sync.FuzzyPhoneNumberHelper.BeninFuzzyMatcher

class BeninFuzzyMatcherTest {

  @Test
  fun matches() {
    val subject = BeninFuzzyMatcher()

    Assert.assertTrue(subject.matches("+22912345678"))
    Assert.assertTrue(subject.matches("+2290112345678"))

    Assert.assertFalse(subject.matches("+2290212345678"))
    Assert.assertFalse(subject.matches("+229999999999"))
    Assert.assertFalse(subject.matches("+11235550101"))
  }

  @Test
  fun getVariant() {
    val subject = BeninFuzzyMatcher()

    Assert.assertEquals("+22912345678", subject.getVariant("+2290112345678"))
    Assert.assertEquals("+2290112345678", subject.getVariant("+22912345678"))

    Assert.assertNull(subject.getVariant(""))
    Assert.assertNull(subject.getVariant("+535512345678"))
  }

  @Test
  fun isPreferredVariant() {
    val subject = BeninFuzzyMatcher()

    Assert.assertTrue(subject.isPreferredVariant("+2290112345678"))
    Assert.assertFalse(subject.isPreferredVariant("+22912345678"))
  }
}
