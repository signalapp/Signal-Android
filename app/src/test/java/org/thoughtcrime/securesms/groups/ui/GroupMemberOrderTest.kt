/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class GroupMemberOrderTest {

  private lateinit var previousLocale: Locale

  @Before
  fun setUp() {
    previousLocale = Locale.getDefault()
    Locale.setDefault(Locale.GERMANY)
  }

  @After
  fun tearDown() {
    Locale.setDefault(previousLocale)
  }

  @Test
  fun `displayNameComparator - accented names sort alongside their unaccented equivalents`() {
    val names = listOf("Zeta", "Ärzte", "Anna", "Émile", "Bernd", "Ökonom", "Oskar")

    val sorted = names.sortedWith(GroupMemberOrder.displayNameComparator({ true }, { it }))

    assertThat(sorted).isEqualTo(listOf("Anna", "Ärzte", "Bernd", "Émile", "Ökonom", "Oskar", "Zeta"))
  }

  @Test
  fun `comparator - self first, then admins, then named members alphabetically`() {
    val self = Member("Zoe", isSelf = true)
    val admin = Member("Yannick", isAdmin = true)
    val bernd = Member("Bernd")
    val arzte = Member("Ärzte")
    val unnamed = Member("+15551234567", hasDisplayName = false)

    val sorted = listOf(bernd, unnamed, arzte, admin, self).sortedWith(MEMBER_ORDER)

    assertThat(sorted).isEqualTo(listOf(self, admin, arzte, bernd, unnamed))
  }

  private data class Member(
    val name: String,
    val isSelf: Boolean = false,
    val isAdmin: Boolean = false,
    val hasDisplayName: Boolean = true
  )

  companion object {
    private val MEMBER_ORDER = GroupMemberOrder.comparator<Member>({ it.isSelf }, { it.isAdmin }, { it.hasDisplayName }, { it.name })
  }
}
