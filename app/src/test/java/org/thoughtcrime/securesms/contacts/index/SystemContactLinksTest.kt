/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.index

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.RecipientTable

class SystemContactLinksTest {

  @Test
  fun `an exact lookup key matches`() {
    val links = links(listOf(link("3854i1fd3.2399r9-2F47", 10)), current = listOf("3854i1fd3.2399r9-2F47"))

    assertThat(links.find("3854i1fd3.2399r9-2F47").ids()).containsExactly(10L)
  }

  @Test
  fun `a stale lookup key matches on a shared segment`() {
    val links = links(listOf(link("3854r15-2F47.2399r16-2F47", 24)), current = listOf("3854i344d.2399r16-2F47"))

    assertThat(links.find("3854i344d.2399r16-2F47").ids()).containsExactly(24L)
  }

  @Test
  fun `an exact match wins over a shared segment`() {
    val links = links(
      listOf(link("3854iaaa.2399r16-2F47", 1), link("3854ibbb", 2)),
      current = listOf("3854ibbb")
    )

    assertThat(links.find("3854ibbb").ids()).containsExactly(2L)
  }

  @Test
  fun `a key that still exists is never matched by its segments`() {
    val links = links(listOf(link("3854iaaa.2399r16-2F47", 1)), current = listOf("3854iaaa.2399r16-2F47", "2399r16-2F47"))

    assertThat(links.find("2399r16-2F47")).isEmpty()
  }

  @Test
  fun `a contact with no shared segment is not linked`() {
    val links = links(listOf(link("3854iaaa.2399r16-2F47", 1)), current = listOf("3854ibbb.2399r17-2F48"))

    assertThat(links.find("3854ibbb.2399r17-2F48")).isEmpty()
  }

  @Test
  fun `two accounts on one contact are both kept`() {
    val links = links(listOf(link("3854iaaa", 1), link("3854iaaa", 2)), current = listOf("3854iaaa"))

    assertThat(links.find("3854iaaa").ids()).containsExactlyInAnyOrder(1L, 2L)
  }

  @Test
  fun `a stale key split across two contacts only links the one with the recipient's number`() {
    val links = links(
      listOf(link("3854ig1.3854ig2", 1, e164 = "+15555550101")),
      current = listOf("3854ig1", "3854ig2")
    )

    assertThat(links.find("3854ig1") { setOf("+15555550199") }).isEmpty()
    assertThat(links.find("3854ig2") { setOf("+15555550101") }.ids()).containsExactly(1L)
  }

  @Test
  fun `a stale key split across two contacts is not linked when neither has the number`() {
    val links = links(listOf(link("3854ig1.3854ig2", 1, e164 = null)), current = listOf("3854ig1", "3854ig2"))

    assertThat(links.find("3854ig1") { setOf("+15555550101") }).isEmpty()
    assertThat(links.find("3854ig2") { setOf("+15555550101") }).isEmpty()
  }

  @Test
  fun `numbers are not read for an unambiguous fallback`() {
    val links = links(listOf(link("3854r15.2399r16", 24)), current = listOf("3854i344d.2399r16"))
    var read = false

    links.find("3854i344d.2399r16") {
      read = true
      emptySet()
    }

    assertThat(read).isFalse()
  }

  @Test
  fun `an escaped dot in a source id does not split the segment`() {
    val links = links(listOf(link("3854iuser..name.2399r16-2F47", 1)), current = listOf("3854iuser", "3854iuser..name.3854ixyz"))

    assertThat(links.find("3854iuser")).isEmpty()
    assertThat(links.find("3854iuser..name.3854ixyz").ids()).containsExactly(1L)
  }

  private fun links(links: List<RecipientTable.SystemContactLink>, current: List<String>): SystemContactLinks {
    return SystemContactLinks(links, current)
  }

  private fun SystemContactLinks.find(lookupKey: String): List<RecipientTable.SystemContactLink> = find(lookupKey) { emptySet() }

  private fun link(lookupKey: String, id: Long, e164: String? = null): RecipientTable.SystemContactLink {
    return RecipientTable.SystemContactLink(lookupKey = lookupKey, recipientId = RecipientId.from(id), e164 = e164, nickname = null)
  }

  private fun List<RecipientTable.SystemContactLink>.ids(): List<Long> = map { it.recipientId.toLong() }
}
