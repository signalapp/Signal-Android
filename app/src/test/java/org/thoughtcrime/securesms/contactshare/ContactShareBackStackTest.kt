/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts

class ContactShareBackStackTest {

  private val share = ContactShareRoute.Share(SharedContactSource.SignalContact(RecipientId.from(1)))
  private val editName = ContactShareRoute.EditName(ContactNameParts(givenName = "Paige"))

  @Test
  fun `going to a route from the current screen pushes it`() {
    val backStack = NavBackStack<NavKey>(ContactShareRoute.SelectContact)

    backStack.goTo(ContactShareRoute.SelectContact, share)

    assertThat(backStack).containsExactly(ContactShareRoute.SelectContact, share)
  }

  @Test
  fun `a repeated push from a screen no longer on top is ignored`() {
    val backStack = NavBackStack<NavKey>(ContactShareRoute.SelectContact)

    backStack.goTo(ContactShareRoute.SelectContact, share)
    backStack.goTo(ContactShareRoute.SelectContact, share)

    assertThat(backStack).containsExactly(ContactShareRoute.SelectContact, share)
  }

  @Test
  fun `a repeated pop from a screen no longer on top is ignored`() {
    val backStack = NavBackStack<NavKey>(share, editName)
    var exits = 0

    backStack.goBackFrom(editName) { exits++ }
    backStack.goBackFrom(editName) { exits++ }

    assertThat(backStack).containsExactly(share)
    assertThat(exits).isEqualTo(0)
  }

  @Test
  fun `going back from the start screen exits rather than emptying the stack`() {
    val backStack = NavBackStack<NavKey>(share)
    var exits = 0

    backStack.goBackFrom(share) { exits++ }

    assertThat(backStack).containsExactly(share)
    assertThat(exits).isEqualTo(1)
  }
}
