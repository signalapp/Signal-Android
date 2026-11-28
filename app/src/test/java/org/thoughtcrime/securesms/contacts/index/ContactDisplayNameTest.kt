/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import java.util.Locale

class ContactDisplayNameTest {

  @Test
  fun `signal contact prefers nickname over everything`() {
    val name = ContactDisplayName.forSignalContact(
      nickname = "Nickname",
      systemName = "System Name",
      profileName = "Profile Name",
      username = "username.01",
      e164 = "+15551234567",
      email = "a@b.com"
    )

    assertThat(name).isEqualTo("Nickname")
  }

  @Test
  fun `signal contact prefers system name over profile name`() {
    val name = ContactDisplayName.forSignalContact(
      nickname = null,
      systemName = "System Name",
      profileName = "Profile Name",
      username = null,
      e164 = null,
      email = null
    )

    assertThat(name).isEqualTo("System Name")
  }

  @Test
  fun `signal contact falls through blanks`() {
    val name = ContactDisplayName.forSignalContact(
      nickname = "",
      systemName = "   ",
      profileName = null,
      username = "username.01",
      e164 = "+15551234567",
      email = null
    )

    assertThat(name).isEqualTo("username.01")
  }

  @Test
  fun `signal contact with nothing to show has no name`() {
    val name = ContactDisplayName.forSignalContact(null, null, null, null, null, null)

    assertThat(name).isNull()
  }

  @Test
  fun `system contact prefers nickname over the provider name`() {
    assertThat(ContactDisplayName.forSystemContact("Provider Name", "Nickname")).isEqualTo("Nickname")
    assertThat(ContactDisplayName.forSystemContact("Provider Name", null)).isEqualTo("Provider Name")
  }

  @Test
  fun `system contact with no name at all has no name`() {
    assertThat(ContactDisplayName.forSystemContact(null, null)).isNull()
  }

  @Test
  fun `section is the uppercased first letter`() {
    assertThat(ContactDisplayName.sectionFor("alice", hasPersonalName = true, hasNickname = false)).isEqualTo("A")
    assertThat(ContactDisplayName.sectionFor("Bob", hasPersonalName = true, hasNickname = false)).isEqualTo("B")
  }

  @Test
  fun `section folds accents so accented names are not stranded`() {
    assertThat(ContactDisplayName.sectionFor("Éric", hasPersonalName = true, hasNickname = false)).isEqualTo("E")
    assertThat(ContactDisplayName.sectionFor("Ólafur", hasPersonalName = true, hasNickname = false)).isEqualTo("O")
  }

  @Test
  fun `section is other for names that do not start with a letter`() {
    assertThat(ContactDisplayName.sectionFor("+15551234567", hasPersonalName = true, hasNickname = false)).isEqualTo(ContactDisplayName.SECTION_OTHER)
    assertThat(ContactDisplayName.sectionFor("911", hasPersonalName = true, hasNickname = false)).isEqualTo(ContactDisplayName.SECTION_OTHER)
    assertThat(ContactDisplayName.sectionFor("", hasPersonalName = true, hasNickname = false)).isEqualTo(ContactDisplayName.SECTION_OTHER)
  }

  @Test
  fun `section ignores leading whitespace`() {
    assertThat(ContactDisplayName.sectionFor("   Zoe", hasPersonalName = true, hasNickname = false)).isEqualTo("Z")
  }

  @Test
  fun `a name standing in for a personal name is sectioned as other`() {
    assertThat(ContactDisplayName.sectionFor("Pacific Plumbing", hasPersonalName = false, hasNickname = false))
      .isEqualTo(ContactDisplayName.SECTION_OTHER)
    assertThat(ContactDisplayName.sectionFor("paige@signal.org", hasPersonalName = false, hasNickname = false))
      .isEqualTo(ContactDisplayName.SECTION_OTHER)
  }

  @Test
  fun `a nicknamed entry is sectioned under the nickname even without a personal name`() {
    assertThat(ContactDisplayName.sectionFor("Plumber Dave", hasPersonalName = false, hasNickname = true)).isEqualTo("P")
  }

  @Test
  fun `a person who has an organization is sectioned by their own name`() {
    // Organization is a field on a named contact, so having one says nothing about being a company.
    assertThat(ContactDisplayName.sectionFor("Luke Skywalker", hasPersonalName = true, hasNickname = false)).isEqualTo("L")
  }

  @Test
  fun `fold lowercases and strips diacritics`() {
    assertThat(ContactDisplayName.normalize("Éric ÅKESSON")).isEqualTo("eric akesson")
  }

  @Test
  fun `search text is wrapped in spaces so word prefixes are matchable`() {
    assertThat(ContactDisplayName.searchTextOf("Alice Smith")).isEqualTo(" alice smith ")
  }

  @Test
  fun `search text merges every known name and dedupes`() {
    val text = ContactDisplayName.searchTextOf("Alice Smith", "Alice Smith", "Ali", null, "")

    assertThat(text).isEqualTo(" alice smith ali ")
  }

  @Test
  fun `search text collapses extra whitespace into single tokens`() {
    assertThat(ContactDisplayName.searchTextOf("  Alice   Smith  ")).isEqualTo(" alice smith ")
  }

  @Test
  fun `search text of nothing is still matchable without crashing`() {
    assertThat(ContactDisplayName.searchTextOf(null, "")).isEqualTo(" ")
  }

  @Test
  fun `sort keys order names case and accent insensitively`() {
    val generator = ContactSortKeyGenerator(Locale.US)

    val alice = generator.of("alice")
    val aliceUpper = generator.of("ALICE")
    val bob = generator.of("Bob")

    assertThat(compare(alice, aliceUpper)).isEqualTo(0)
    assertThat(compare(alice, bob) < 0).isEqualTo(true)
  }

  @Test
  fun `sort keys put accented names next to their unaccented form rather than after z`() {
    val generator = ContactSortKeyGenerator(Locale.US)

    val eric = generator.of("Eric")
    val ericAccented = generator.of("Éric")
    val zoe = generator.of("Zoe")

    assertThat(compare(eric, ericAccented)).isEqualTo(0)
    assertThat(compare(ericAccented, zoe) < 0).isEqualTo(true)
  }

  @Test
  fun `name query becomes a word prefix pattern`() {
    assertThat(ContactDisplayName.searchPatternFor("ali")).isEqualTo("% ali%")
  }

  @Test
  fun `name query is folded so an unaccented query finds an accented name`() {
    assertThat(ContactDisplayName.searchPatternFor("ÉRIC")).isEqualTo("% eric%")
  }

  @Test
  fun `like wildcards typed by the user are escaped rather than treated as wildcards`() {
    assertThat(ContactDisplayName.searchPatternFor("100%")).isEqualTo("% 100\\%%")
    assertThat(ContactDisplayName.searchPatternFor("a_b")).isEqualTo("% a\\_b%")
    assertThat(ContactDisplayName.searchPatternFor("a%b")).isEqualTo("% a\\%b%")
    assertThat(ContactDisplayName.searchPatternFor("a\\b")).isEqualTo("% a\\\\b%")
  }

  @Test
  fun `numeric query matches anywhere so a partial number is findable`() {
    assertThat(ContactDisplayName.searchPatternFor("5551234")).isEqualTo("%5551234%")
    assertThat(ContactDisplayName.searchPatternFor("+1 (555) 123-4567")).isEqualTo("%15551234567%")
  }

  @Test
  fun `blank query is a word prefix pattern that matches every row`() {
    assertThat(ContactDisplayName.searchPatternFor("   ")).isEqualTo("% %")
  }

  /** Mirrors SQLite's blob comparison, which is a memcmp over the stored bytes. */
  private fun compare(left: ByteArray, right: ByteArray): Int {
    for (i in 0 until minOf(left.size, right.size)) {
      val diff = (left[i].toInt() and 0xFF) - (right[i].toInt() and 0xFF)
      if (diff != 0) {
        return diff
      }
    }
    return left.size - right.size
  }
}
