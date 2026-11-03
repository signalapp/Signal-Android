/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.contacts

import android.database.MatrixCursor
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.contacts.SystemContactsRepository.ContactDetails
import org.signal.contacts.SystemContactsRepository.CursorContactIterator

@RunWith(RobolectricTestRunner::class)
class CursorContactIteratorTest {

  @Test
  fun `reads every contact when the cursor does not fail`() {
    val cursor = FailingCursor(BASE_COLUMNS, failAt = Int.MAX_VALUE).apply {
      addPhone("alice", "+15555550100")
      addName("alice", "Alice")
      addPhone("bob", "+15555550200")
    }

    val iterator = CursorContactIterator(cursor, OWN_ACCOUNT_TYPE) { it }
    val contacts = iterator.readAll()

    assertEquals(listOf("Alice", null), contacts.map { it.givenName })
    assertEquals(listOf(listOf("+15555550100"), listOf("+15555550200")), contacts.map { contact -> contact.numbers.map { it.number } })
    assertFalse(iterator.stoppedEarly)
  }

  @Test
  fun `stops early without crashing when the cursor fails while reading phones`() {
    val cursor = FailingCursor(BASE_COLUMNS, failAt = 1).apply {
      addPhone("alice", "+15555550100")
      addPhone("alice", "+15555550101")
      addPhone("bob", "+15555550200")
    }

    val iterator = CursorContactIterator(cursor, OWN_ACCOUNT_TYPE) { it }
    val contacts = iterator.readAll()

    assertEquals(1, contacts.size)
    assertEquals(listOf("+15555550100"), contacts[0].numbers.map { it.number })
    assertTrue(iterator.stoppedEarly)
  }

  @Test
  fun `stops early without crashing when the cursor fails while reading names`() {
    val cursor = FailingCursor(BASE_COLUMNS + NAME_SOURCE_COLUMNS, failAt = 2).apply {
      addPhone("alice", "+15555550100")
      addName("alice", "Alice", rawContactId = 1, nameRawContactId = 2, accountType = "other")
      addName("alice", "Alicia", rawContactId = 2, nameRawContactId = 2, accountType = "other")
      addPhone("bob", "+15555550200")
    }

    val iterator = CursorContactIterator(cursor, OWN_ACCOUNT_TYPE) { it }
    val contacts = iterator.readAll()

    assertEquals(1, contacts.size)
    assertEquals("Alice", contacts[0].givenName)
    assertTrue(iterator.stoppedEarly)
  }

  private fun CursorContactIterator.readAll(): List<ContactDetails> {
    val contacts = mutableListOf<ContactDetails>()
    while (hasNext()) {
      contacts += next()
    }
    return contacts
  }

  private class FailingCursor(columns: Array<String>, private val failAt: Int) : MatrixCursor(columns) {
    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
      return newPosition < failAt
    }

    fun addPhone(lookupKey: String, number: String) {
      newRow()
        .add(Data.LOOKUP_KEY, lookupKey)
        .add(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
        .add(Phone.NUMBER, number)
        .add(Phone.TYPE, Phone.TYPE_MOBILE)
        .add(Data.CONTACT_ID, 1L)
    }

    fun addName(lookupKey: String, givenName: String, rawContactId: Long = 1, nameRawContactId: Long = 1, accountType: String = "other") {
      newRow()
        .add(Data.LOOKUP_KEY, lookupKey)
        .add(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
        .add(StructuredName.GIVEN_NAME, givenName)
        .add(Data.CONTACT_ID, 1L)
        .add(Data.RAW_CONTACT_ID, rawContactId)
        .add(Contacts.NAME_RAW_CONTACT_ID, nameRawContactId)
        .add(RawContacts.ACCOUNT_TYPE, accountType)
    }
  }

  companion object {
    private const val OWN_ACCOUNT_TYPE = "org.signal.test"

    private val BASE_COLUMNS = arrayOf(
      Data.LOOKUP_KEY,
      Data.MIMETYPE,
      Phone.NUMBER,
      Phone.TYPE,
      Phone.LABEL,
      Data.CONTACT_ID,
      Phone.DISPLAY_NAME,
      Phone.PHOTO_URI
    )

    private val NAME_SOURCE_COLUMNS = arrayOf(
      Data.RAW_CONTACT_ID,
      Contacts.NAME_RAW_CONTACT_ID,
      RawContacts.ACCOUNT_TYPE
    )
  }
}
