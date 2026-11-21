/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Test
import org.thoughtcrime.securesms.database.WordFragmentMatcher.Match

class WordFragmentMatcherTest {

  @Test
  fun `fragments that each begin a word match`() {
    val matches = WordFragmentMatcher.matches("raha", "raised hand")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 7))
  }

  @Test
  fun `a fragment in the middle of a word does not match`() {
    val matches = WordFragmentMatcher.matches("hrt", "heart")

    assertThat(matches).isEmpty()
  }

  @Test
  fun `letters that are in order but do not begin words do not match`() {
    val matches = WordFragmentMatcher.matches("raha", "ideograph advantage")

    assertThat(matches).isEmpty()
  }

  @Test
  fun `fragments in a different order than the words do not match`() {
    val matches = WordFragmentMatcher.matches("hara", "raised hand")

    assertThat(matches).isEmpty()
  }

  @Test
  fun `case is ignored`() {
    val matches = WordFragmentMatcher.matches("RaHa", "Raised Hand")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 7))
  }

  @Test
  fun `words between fragments can be skipped`() {
    val matches = WordFragmentMatcher.matches("raha", "raised back of hand")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 15))
  }

  @Test
  fun `a word begins after any character that is not part of a word`() {
    val matches = WordFragmentMatcher.matches("smge", "Smiley-Gesicht")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 10))
  }

  @Test
  fun `a vowel sign does not end a word`() {
    val matches = WordFragmentMatcher.matches("हथ", "हाथ")

    assertThat(matches).isEmpty()
  }

  @Test
  fun `a fragment is shortened when the longest one leaves the rest unmatched`() {
    val matches = WordFragmentMatcher.matches("theat", "the heat")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 3))
  }

  @Test
  fun `a space in the query starts a new fragment`() {
    val matches = WordFragmentMatcher.matches("ra ha", "raised hand")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 7))
  }

  @Test
  fun `the fragment after a space in the query has to begin a word`() {
    val matches = WordFragmentMatcher.matches("ra is", "raised")

    assertThat(matches).isEmpty()
  }

  @Test
  fun `every word the query can start at gives a match`() {
    val matches = WordFragmentMatcher.matches("ab", "a b ab")

    assertThat(matches).containsExactly(
      Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 4),
      Match(fragmentCount = 1, prefixCount = 4, unmatchedCount = 0)
    )
  }

  @Test
  fun `a match has the fewest fragments possible from its start`() {
    val matches = WordFragmentMatcher.matches("abcd", "ab b cd")

    assertThat(matches).containsExactly(Match(fragmentCount = 2, prefixCount = 0, unmatchedCount = 3))
  }
}
