/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.details

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.Locale
import java.util.Optional

/**
 * Covers the mapping of a received card into screen state. The recipient lookup is stubbed out, so
 * these are about what the card itself produces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedContactDetailsRepositoryTest {

  companion object {
    private const val ACI_STRING = "3f0c1d5e-9b2a-4c7d-8e1f-6a5b4c3d2e1f"
  }

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(AccountValues::class))

  private val repository = SharedContactDetailsRepository(
    context = ApplicationProvider.getApplicationContext(),
    locale = Locale.US
  )

  @Before
  fun setUp() {
    // A relaxed AccountValues hands back "" rather than null, which the number formatter cannot parse.
    every { signalStore.account.e164 } returns "+15105550000"

    mockkObject(RemoteConfig)
    every { RemoteConfig.contactSharingV2 } returns true

    mockkObject(SignalDatabase)
    every { SignalDatabase.recipients } returns mockk {
      every { getByE164(any()) } returns Optional.empty()
    }

    // Resolution now checks registration, and a matched row is registered unless a test says otherwise.
    mockkObject(Recipient.Companion)
    every { Recipient.resolved(any()) } returns mockk { every { isRegistered } returns true }
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
    unmockkObject(SignalDatabase)
    unmockkObject(Recipient.Companion)
  }

  @Test
  fun `a company alongside a name is offered under the name`() = runTest {
    val state = repository.loadState(contact(name = name("Paige", "Hall"), organization = "Signal Messenger"))

    assertThat(state.displayName).isEqualTo("Paige Hall")
    assertThat(state.organization).isEqualTo("Signal Messenger")
  }

  @Test
  fun `a company that is the only name is not repeated under itself`() = runTest {
    val state = repository.loadState(contact(organization = "Pacific Plumbing"))

    assertThat(state.displayName).isEqualTo("Pacific Plumbing")
    assertThat(state.organization).isNull()
  }

  @Test
  fun `a company only card has no initials worth drawing`() = runTest {
    assertThat(repository.loadState(contact(organization = "Pacific Plumbing")).hasPersonalName).isFalse()
    assertThat(repository.loadState(contact(name = name("Paige", "Hall"))).hasPersonalName).isTrue()
  }

  @Test
  fun `every shared detail becomes a row, in a stable order`() = runTest {
    val state = repository.loadState(
      contact(
        name = name("Paige", "Hall"),
        phones = listOf("+15105550101"),
        emails = listOf("paigehall@example.com"),
        addresses = listOf("123 Beach Drive")
      )
    )

    assertThat(state.details.map { it.id }).containsExactly("phone:0", "email:0", "address:0")
    assertThat(state.details.map { it.kind }).containsExactly(
      SharedContactDetailsState.DetailKind.PHONE,
      SharedContactDetailsState.DetailKind.EMAIL,
      SharedContactDetailsState.DetailKind.ADDRESS
    )
  }

  @Test
  fun `a card with no matched recipient is not on Signal`() = runTest {
    val state = repository.loadState(contact(name = name("Paige", "Hall"), phones = listOf("+15105550101")))

    assertThat(state.isOnSignal).isFalse()
    assertThat(state.showCallButtons).isFalse()
  }

  @Test
  fun `an address only card can still be saved but not invited`() = runTest {
    val state = repository.loadState(contact(organization = "Pacific Plumbing", addresses = listOf("123 Beach Drive")))

    assertThat(state.actions).containsExactly(SharedContactDetailsState.ContactAction.ADD_TO_PHONE_CONTACTS)
  }

  @Test
  fun `a card carrying only an ACI is on Signal and is not offered an invite`() = runTest {
    stubRecipients(byAci = Optional.empty(), insertedFromAci = RecipientId.from(7))

    val state = repository.loadState(contact(name = name("Paige", "Hall"), aci = ACI_STRING))

    assertThat(state.actions).doesNotContain(SharedContactDetailsState.ContactAction.INVITE_TO_SIGNAL)
  }

  /** Browsing a card must not write a row, so the recipient is only created once something is pressed. */
  @Test
  fun `loading an ACI card does not create a recipient`() = runTest {
    val recipients = stubRecipients(byAci = Optional.empty(), insertedFromAci = RecipientId.from(7))

    val state = repository.loadState(contact(name = name("Paige", "Hall"), aci = ACI_STRING))

    assertThat(state.signalRecipientId).isNull()
    verify(exactly = 0) { recipients.getOrInsertFromServiceId(any()) }
  }

  @Test
  fun `resolving for an action creates the recipient from the ACI`() = runTest {
    val seeded = RecipientId.from(7)
    val recipients = stubRecipients(byAci = Optional.empty(), insertedFromAci = seeded)

    val resolved = repository.resolveOrCreateRecipient(contact(name = name("Paige", "Hall"), aci = ACI_STRING))

    assertThat(resolved).isEqualTo(seeded)
    verify(exactly = 1) { recipients.getOrInsertFromServiceId(any()) }
  }

  @Test
  fun `an ACI that already has a row is matched rather than inserted`() = runTest {
    val existing = RecipientId.from(11)
    val recipients = stubRecipients(byAci = Optional.of(existing), insertedFromAci = RecipientId.from(99))

    val state = repository.loadState(contact(name = name("Paige", "Hall"), aci = ACI_STRING))

    assertThat(state.signalRecipientId).isEqualTo(existing)
    verify(exactly = 0) { recipients.getOrInsertFromServiceId(any()) }
    assertThat(state.actions).doesNotContain(SharedContactDetailsState.ContactAction.INVITE_TO_SIGNAL)
  }

  @Test
  fun `a card with neither an ACI nor a known number is offered an invite`() = runTest {
    val state = repository.loadState(contact(name = name("Paige", "Hall"), phones = listOf("+15105550101")))

    assertThat(state.signalRecipientId).isNull()
    assertThat(state.actions).contains(SharedContactDetailsState.ContactAction.INVITE_TO_SIGNAL)
  }

  @Test
  fun `a shared nickname and note are rendered as their own rows`() = runTest {
    val state = repository.loadState(
      contact(
        name = name("Paige", "Hall"),
        phones = listOf("+15105550101"),
        nickname = Contact.SignalNickname("Paige", "H"),
        note = "Met in 2017"
      )
    )

    assertThat(state.details.map { it.kind }).containsExactly(
      SharedContactDetailsState.DetailKind.PHONE,
      SharedContactDetailsState.DetailKind.NICKNAME,
      SharedContactDetailsState.DetailKind.NOTE
    )
    assertThat(state.details.first { it.kind == SharedContactDetailsState.DetailKind.NICKNAME }.lines).containsExactly("Paige H")
    assertThat(state.details.first { it.kind == SharedContactDetailsState.DetailKind.NOTE }.lines).containsExactly("Met in 2017")
  }

  @Test
  fun `a card carrying neither renders no nickname or note row`() = runTest {
    val state = repository.loadState(contact(name = name("Paige", "Hall"), phones = listOf("+15105550101")))

    assertThat(state.details.map { it.kind }).containsExactly(SharedContactDetailsState.DetailKind.PHONE)
  }

  @Test
  fun `an empty nickname and a blank note render no rows`() = runTest {
    val state = repository.loadState(
      contact(
        name = name("Paige", "Hall"),
        nickname = Contact.SignalNickname(null, null),
        note = "   "
      )
    )

    assertThat(state.details).containsExactly()
  }

  private fun stubRecipients(byAci: Optional<RecipientId>, insertedFromAci: RecipientId): RecipientTable {
    val recipients: RecipientTable = mockk {
      every { getByE164(any()) } returns Optional.empty()
      every { getByAci(any()) } returns byAci
      every { getOrInsertFromServiceId(any()) } returns insertedFromAci
      every { setSharedName(any(), any()) } returns Unit
    }

    every { SignalDatabase.recipients } returns recipients

    return recipients
  }

  private fun name(given: String? = null, family: String? = null) = Contact.Name(given, family, null, null, null, null)

  private fun contact(
    name: Contact.Name = Contact.Name(null, null, null, null, null, null),
    organization: String? = null,
    phones: List<String> = emptyList(),
    emails: List<String> = emptyList(),
    addresses: List<String> = emptyList(),
    aci: String? = null,
    nickname: Contact.SignalNickname? = null,
    note: String? = null
  ): Contact {
    return Contact(
      name,
      organization,
      phones.map { Contact.Phone(it, Contact.Phone.Type.MOBILE, null) },
      emails.map { Contact.Email(it, Contact.Email.Type.HOME, null) },
      addresses.map { Contact.PostalAddress(Contact.PostalAddress.Type.HOME, null, it, null, null, null, null, null, null) },
      null,
      aci,
      nickname,
      note
    )
  }
}
