/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.database.MatrixCursor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.ServiceId.ACI
import org.thoughtcrime.securesms.database.model.ThreadRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import java.util.UUID

/**
 * Tests for [ThreadTable.StaticReader.getCurrent] nullability contract.
 *
 * Validates that getCurrent() always returns a non-null [ThreadRecord],
 * restoring the behavior expected by [SearchRepository] and [MessageSearchResult].
 *
 * See: https://github.com/signalapp/Signal-Android/issues/14641
 */
@Suppress("ClassName")
@RunWith(AndroidJUnit4::class)
class ThreadTableTest_getCurrent {

  @Rule
  @JvmField
  val databaseRule = SignalDatabaseRule()

  private lateinit var recipient: Recipient

  @Before
  fun setUp() {
    recipient = Recipient.resolved(SignalDatabase.recipients.getOrInsertFromServiceId(ACI.from(UUID.randomUUID())))
  }

  @Test
  fun givenValidThread_whenGetCurrent_thenReturnsNonNullRecord() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, body = "Hello world", threadId = threadId)
    SignalDatabase.threads.update(threadId, false)

    // WHEN
    val record = SignalDatabase.threads.getThreadRecord(threadId)

    // THEN
    assertNotNull("getCurrent() must return a non-null ThreadRecord for a valid thread", record)
    assertEquals(threadId, record!!.threadId)
    assertEquals(recipient.id, record.recipient.id)
  }

  @Test
  fun givenValidThread_whenGetCurrentViaReader_thenReturnsNonNullRecord() {
    // GIVEN
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipient)
    MmsHelper.insert(recipient = recipient, body = "Test message", threadId = threadId)
    SignalDatabase.threads.update(threadId, false)

    // WHEN - simulate the SearchRepository path: readerFor(cursor).getCurrent()
    val record = SignalDatabase.threads.getThreadRecord(threadId)

    // THEN - the Kotlin return type is now ThreadRecord (non-null)
    assertNotNull("getCurrent() must never return null", record)
    assertEquals("Test message", record!!.body)
  }

  @Test
  fun givenMalformedCursor_whenGetCurrent_thenReturnsFallbackRecord() {
    // GIVEN - create a cursor with missing columns to simulate a parsing failure
    val malformedCursor = MatrixCursor(arrayOf("_id"))
    malformedCursor.addRow(arrayOf(42L))
    malformedCursor.moveToFirst()

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // WHEN - getCurrent() should catch the exception and return a fallback
    val reader = ThreadTable.StaticReader(malformedCursor, context)
    val record = reader.getCurrent()

    // THEN - record should be non-null with default values
    assertNotNull("getCurrent() must return a non-null fallback record even with malformed cursor", record)
    assertEquals("Fallback body should be empty string", "", record.body)
    assertEquals("Fallback recipient should be Recipient.UNKNOWN", Recipient.UNKNOWN, record.recipient)
  }

  @Test
  fun givenCompletelyEmptyCursor_whenGetCurrent_thenReturnsFallbackWithZeroThreadId() {
    // GIVEN - a cursor with no columns at all
    val emptyCursor = MatrixCursor(arrayOf("nonexistent_column"))
    emptyCursor.addRow(arrayOf("value"))
    emptyCursor.moveToFirst()

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    // WHEN
    val reader = ThreadTable.StaticReader(emptyCursor, context)
    val record = reader.getCurrent()

    // THEN - should return fallback with threadId 0 (since _id column is missing)
    assertNotNull("getCurrent() must return non-null even with completely wrong cursor schema", record)
    assertEquals("Fallback thread id should be 0 when _id column is missing", 0L, record.threadId)
    assertEquals("Fallback body should be empty string", "", record.body)
  }
}
