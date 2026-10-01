/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.contacts

import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SystemContactsRepositoryTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    FakeContactsProvider.reset()
    Robolectric.buildContentProvider(FakeContactsProvider::class.java).create(ContactsContract.AUTHORITY)
  }

  @After
  fun tearDown() {
    FakeContactsProvider.reset()
  }

  @Test
  fun `a full read groups rows by contact, keeping the lowest type per number and the name source's name`() {
    FakeContactsProvider.dataRows = BOB_ROWS + ALICE_ROWS

    val contacts = readAll()

    assertEquals(2, contacts.size)

    val alice = contacts[0]
    assertEquals("Alice", alice.givenName)
    assertEquals("Anderson", alice.familyName)
    assertEquals(listOf("+15555550101", "+15555550199"), alice.numbers.map { it.number })
    assertEquals(Phone.TYPE_HOME, alice.numbers[0].type)
    assertEquals("content://com.android.contacts/contacts/lookup/alice/11", alice.numbers[0].contactUri.toString())
    assertEquals("Alice Anderson", alice.numbers[0].displayName)

    val bob = contacts[1]
    assertNull(bob.givenName)
    assertEquals(emptyList<String>(), bob.numbers.map { it.number })
  }

  @Test
  fun `without the name source columns, the latest name row is used`() {
    FakeContactsProvider.rejectNameSourceColumns = true
    FakeContactsProvider.dataRows = ALICE_ROWS

    val alice = readAll().single()

    assertEquals("Wrong", alice.givenName)
  }

  @Test
  fun `a full read keeps each number's label and the contact's photo`() {
    FakeContactsProvider.dataRows = listOf(
      phone(4, "dana", id = 40, rawContactId = 401, accountType = GOOGLE, displayName = "Dana", number = "555-555-0144", type = Phone.TYPE_CUSTOM, label = "Studio")
    )

    val number = readAll().single().numbers.single()

    assertEquals(Phone.TYPE_CUSTOM, number.type)
    assertEquals("Studio", number.label)
    assertEquals("content://com.android.contacts/contacts/4/photo", number.photoUri)
  }

  private fun readAll(): List<SystemContactsRepository.ContactDetails> {
    return SystemContactsRepository.getAllSystemContacts(context, OWN_ACCOUNT_TYPE, FORMATTER).use { iterator ->
      val contacts = mutableListOf<SystemContactsRepository.ContactDetails>()
      while (iterator.hasNext()) {
        contacts += iterator.next()
      }
      contacts
    }
  }

  companion object {
    private const val OWN_ACCOUNT_TYPE = "org.signal.test"
    private const val GOOGLE = "com.google"

    /** Formats ten-digit numbers as North American E164s, and rejects anything else. */
    private val FORMATTER: (String) -> String? = { number -> number.filter { it.isDigit() }.takeIf { it.length == 10 }?.let { "+1$it" } }

    private fun phone(contactId: Long, lookupKey: String, id: Long, rawContactId: Long, accountType: String, displayName: String, number: String, type: Int, label: String? = null): Map<String, Any?> {
      return mapOf(
        ContactsContract.Data.MIMETYPE to Phone.CONTENT_ITEM_TYPE,
        ContactsContract.Data.CONTACT_ID to contactId,
        Phone.LOOKUP_KEY to lookupKey,
        Phone._ID to id,
        ContactsContract.Data.RAW_CONTACT_ID to rawContactId,
        ContactsContract.RawContacts.ACCOUNT_TYPE to accountType,
        ContactsContract.Contacts.NAME_RAW_CONTACT_ID to 101L,
        Phone.DISPLAY_NAME to displayName,
        Phone.PHOTO_URI to "content://com.android.contacts/contacts/$contactId/photo",
        Phone.NUMBER to number,
        Phone.TYPE to type,
        Phone.LABEL to label
      )
    }

    private fun name(contactId: Long, lookupKey: String, id: Long, rawContactId: Long, accountType: String, nameRawContactId: Long, given: String, family: String?): Map<String, Any?> {
      return mapOf(
        ContactsContract.Data.MIMETYPE to StructuredName.CONTENT_ITEM_TYPE,
        ContactsContract.Data.CONTACT_ID to contactId,
        StructuredName.LOOKUP_KEY to lookupKey,
        StructuredName._ID to id,
        ContactsContract.Data.RAW_CONTACT_ID to rawContactId,
        ContactsContract.RawContacts.ACCOUNT_TYPE to accountType,
        ContactsContract.Contacts.NAME_RAW_CONTACT_ID to nameRawContactId,
        StructuredName.DISPLAY_NAME to "$given ${family ?: ""}".trim(),
        StructuredName.GIVEN_NAME to given,
        StructuredName.FAMILY_NAME to family
      )
    }

    /**
     * Deliberately out of order, so the results depend on the query's sort order: the provider puts
     * phones before names and the latest row first. One number appears twice with different types;
     * Signal's own raw contact contributes the latest name; the name source is raw contact 101.
     */
    private val ALICE_ROWS = listOf(
      name(1, "alice", id = 20, rawContactId = 101, accountType = GOOGLE, nameRawContactId = 101, given = "Alice", family = "Anderson"),
      phone(1, "alice", id = 10, rawContactId = 101, accountType = GOOGLE, displayName = "Alice Anderson", number = "555 555 0199", type = Phone.TYPE_WORK),
      name(1, "alice", id = 22, rawContactId = 900, accountType = OWN_ACCOUNT_TYPE, nameRawContactId = 101, given = "Wrong", family = "Signal"),
      phone(1, "alice", id = 12, rawContactId = 101, accountType = GOOGLE, displayName = "Alice Anderson", number = "555-555-0101", type = Phone.TYPE_MOBILE),
      name(1, "alice", id = 21, rawContactId = 102, accountType = GOOGLE, nameRawContactId = 101, given = "Ally", family = null),
      phone(1, "alice", id = 11, rawContactId = 101, accountType = GOOGLE, displayName = "Alice Anderson", number = "(555) 555-0101", type = Phone.TYPE_HOME)
    )

    /** An invalid number, and a name only from Signal's own raw contact. */
    private val BOB_ROWS = listOf(
      phone(2, "bob", id = 30, rawContactId = 201, accountType = GOOGLE, displayName = "Bob", number = "not a number", type = Phone.TYPE_MOBILE),
      name(2, "bob", id = 31, rawContactId = 902, accountType = OWN_ACCOUNT_TYPE, nameRawContactId = 902, given = "Bob", family = null)
    )
  }
}
