/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.share

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactCardReader
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.contactshare.screens.editname.ContactNameParts
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.RemoteConfig
import java.util.Locale
import java.util.Optional

/**
 * Covers the trimming done on send, which decides what actually reaches the wire.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareContactRepositoryTest {

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(AccountValues::class))

  private val repository = ShareContactRepository(context = ApplicationProvider.getApplicationContext())

  @Before
  fun setUp() {
    every { signalStore.account.e164 } returns "+15105550000"

    mockkObject(RemoteConfig)
    every { RemoteConfig.contactSharingV2 } returns true

    mockkObject(SignalDatabase)
    every { SignalDatabase.recipients } returns mockk {
      every { getByE164(any()) } returns Optional.empty()
    }
  }

  @After
  fun tearDown() {
    unmockkObject(RemoteConfig)
    unmockkObject(SignalDatabase)
  }

  @Test
  fun `a card with a photo offers that photo and the choice to send none`() = runTest {
    val loaded = load(contact(name = Contact.Name("Paige", "Hall", null, null, null, null), avatar = Contact.Avatar(Uri.parse("content://address-book/1"), false)))

    assertThat(loaded.photoOptions.map { it.id }).containsExactly("address-book", "none")
    assertThat(loaded.photoOptions.last().photo).isNull()
  }

  /** One photo is enough to make the row editable, since declining it is the second choice. */
  @Test
  fun `a card with a single photo is still editable`() = runTest {
    val loaded = load(contact(name = Contact.Name("Paige", "Hall", null, null, null, null), avatar = Contact.Avatar(Uri.parse("content://address-book/1"), false)))

    assertThat(loaded.state.avatar?.isEditable).isEqualTo(true)
  }

  @Test
  fun `a card with no photo offers nothing to choose`() = runTest {
    val loaded = load(contact(name = Contact.Name("Paige", "Hall", null, null, null, null)))

    assertThat(loaded.photoOptions).isEmpty()
    assertThat(loaded.state.avatar).isNull()
  }

  @Test
  fun `a company that is the only name is kept even though no row was selected`() {
    val card = repository.buildCard(contact(name = null, organization = "Pacific Plumbing"), selection())

    assertThat(card.organization).isEqualTo("Pacific Plumbing")
  }

  @Test
  fun `a company alongside a name is dropped when its row is deselected`() {
    val contact = contact(name = Contact.Name("Paige", "Hall", null, null, null, null), organization = "Signal Messenger")

    val card = repository.buildCard(contact, selection(detailIds = emptySet()))

    assertThat(card.organization).isNull()
  }

  @Test
  fun `a company alongside a name is kept when its row is selected`() {
    val contact = contact(name = Contact.Name("Paige", "Hall", null, null, null, null), organization = "Signal Messenger")

    val card = repository.buildCard(contact, selection(detailIds = setOf("organization")))

    assertThat(card.organization).isEqualTo("Signal Messenger")
  }

  @Test
  fun `a company is kept when the name it was offered alongside has been cleared`() {
    val contact = contact(name = Contact.Name("Paige", "Hall", null, null, null, null), organization = "Signal Messenger")

    val card = repository.buildCard(contact, selection(name = ContactNameParts(organization = "Signal Messenger"), detailIds = emptySet()))

    assertThat(card.organization).isEqualTo("Signal Messenger")
  }

  @Test
  fun `only the selected details are carried onto the card`() {
    val contact = contact(
      name = Contact.Name("Paige", "Hall", null, null, null, null),
      phones = listOf("+15105550101", "+15105550102"),
      emails = listOf("home@example.com", "work@example.com")
    )

    val card = repository.buildCard(contact, selection(detailIds = setOf("phone:1", "email:0")))

    assertThat(card.phoneNumbers.map { it.number }).containsExactly("+15105550102")
    assertThat(card.emails.map { it.email }).containsExactly("home@example.com")
  }

  @Test
  fun `an unselected name is sent as an empty name rather than the original`() {
    val contact = contact(name = Contact.Name("Paige", "Hall", null, null, null, null))

    val card = repository.buildCard(contact, selection(name = null))

    assertThat(card.name.isEmpty).isEqualTo(true)
  }

  @Test
  fun `the edited name is what reaches the card`() {
    val contact = contact(name = Contact.Name("Paige", "Hall", null, null, null, null))

    val card = repository.buildCard(contact, selection(name = ContactNameParts(givenName = "Edited", familyName = "Name")))

    assertThat(card.name.givenName).isEqualTo("Edited")
    assertThat(card.name.familyName).isEqualTo("Name")
  }

  @Test
  fun `only the first number is offered by default, and the company is not`() = runTest {
    val loaded = load(
      contact(
        name = Contact.Name("Paige", "Hall", null, null, null, null),
        organization = "Signal Messenger",
        phones = listOf("+15105550101", "+15105550102"),
        emails = listOf("home@example.com")
      )
    )

    val selectedById = loaded.state.details.associate { it.id to it.isSelected }

    assertThat(selectedById).isEqualTo(
      mapOf(
        "organization" to false,
        "phone:0" to true,
        "phone:1" to false,
        "email:0" to false
      )
    )
  }

  @Test
  fun `a company that is the only name is not offered as a row`() = runTest {
    val loaded = load(contact(organization = "Pacific Plumbing", phones = listOf("+15105550101")))

    assertThat(loaded.state.details.map { it.id }).containsExactly("phone:0")
  }

  @Test
  fun `the nickname and note are offered as rows, unselected`() = runTest {
    val loaded = load(
      contact(
        name = Contact.Name("Paige", "Hall", null, null, null, null),
        phones = listOf("+15105550101"),
        nickname = Contact.SignalNickname("Paige", "H"),
        note = "Met in 2017"
      )
    )

    val selectedById = loaded.state.details.associate { it.id to it.isSelected }

    assertThat(selectedById).isEqualTo(
      mapOf(
        "phone:0" to true,
        "nickname" to false,
        "note" to false
      )
    )
  }

  @Test
  fun `the nickname row shows both parts joined`() = runTest {
    val loaded = load(contact(nickname = Contact.SignalNickname("Paige", "H"), phones = listOf("+15105550101")))

    assertThat(loaded.state.details.first { it.id == "nickname" }.lines).containsExactly("Paige H")
  }

  @Test
  fun `an empty nickname and a blank note are not offered at all`() = runTest {
    val loaded = load(
      contact(
        phones = listOf("+15105550101"),
        nickname = Contact.SignalNickname(null, null),
        note = "   "
      )
    )

    assertThat(loaded.state.details.map { it.id }).containsExactly("phone:0")
  }

  @Test
  fun `the nickname and note are dropped from the card when their rows are deselected`() {
    val contact = contact(
      name = Contact.Name("Paige", "Hall", null, null, null, null),
      nickname = Contact.SignalNickname("Paige", "H"),
      note = "Met in 2017"
    )

    val card = repository.buildCard(contact, selection(detailIds = emptySet()))

    assertThat(card.nickname).isNull()
    assertThat(card.note).isNull()
  }

  @Test
  fun `the nickname and note reach the card when their rows are selected`() {
    val contact = contact(
      name = Contact.Name("Paige", "Hall", null, null, null, null),
      nickname = Contact.SignalNickname("Paige", "H"),
      note = "Met in 2017"
    )

    val card = repository.buildCard(contact, selection(detailIds = setOf("nickname", "note")))

    assertThat(card.nickname?.given).isEqualTo("Paige")
    assertThat(card.note).isEqualTo("Met in 2017")
  }

  /** Drives the real load path with a stubbed reader, so the defaults come from buildDetails. */
  private suspend fun load(contact: Contact): LoadedContact {
    val reader: ContactCardReader = mockk { every { readSystemContact(any()) } returns contact }
    val repository = ShareContactRepository(
      context = ApplicationProvider.getApplicationContext(),
      locale = Locale.US,
      reader = reader
    )

    return repository.load(source = SharedContactSource.AddressBook(Uri.EMPTY), recipientId = null)!!
  }

  private fun selection(
    name: ContactNameParts? = ContactNameParts(givenName = "Paige", familyName = "Hall"),
    detailIds: Set<String> = emptySet()
  ): ShareContactSelection {
    return ShareContactSelection(name = name, displayName = "Paige Hall", photo = null, detailIds = detailIds)
  }

  private fun contact(
    name: Contact.Name? = null,
    organization: String? = null,
    phones: List<String> = emptyList(),
    emails: List<String> = emptyList(),
    nickname: Contact.SignalNickname? = null,
    note: String? = null,
    avatar: Contact.Avatar? = null
  ): Contact {
    return Contact(
      name ?: Contact.Name(null, null, null, null, null, null),
      organization,
      phones.map { Contact.Phone(it, Contact.Phone.Type.MOBILE, null) },
      emails.map { Contact.Email(it, Contact.Email.Type.HOME, null) },
      emptyList(),
      avatar,
      null,
      nickname,
      note
    )
  }
}
