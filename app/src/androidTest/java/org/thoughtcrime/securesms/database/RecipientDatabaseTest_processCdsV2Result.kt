package org.thoughtcrime.securesms.database

import androidx.core.content.contentValuesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientDatabaseTest_processCdsV2Result {

  private lateinit var recipientDatabase: RecipientDatabase

  private val localAci = ACI.from(UUID.randomUUID())
  private val localPni = PNI.from(UUID.randomUUID())

  @Before
  fun setup() {
    recipientDatabase = SignalDatabase.recipients

    ensureDbEmpty()

    SignalStore.account().setAci(localAci)
    SignalStore.account().setPni(localPni)
  }

  @Test
  fun processCdsV2Result_noMatch() {
    // Note that we haven't inserted any test data

    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(resultId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_fullMatch() {
    val inputId: RecipientId = insert(E164_A, PNI_A, ACI_A)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_onlyE164Matches() {
    val inputId: RecipientId = insert(E164_A, null, null)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_e164AndPniMatches() {
    val inputId: RecipientId = insert(E164_A, PNI_A, null)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_e164AndAciMatches() {
    val inputId: RecipientId = insert(E164_A, null, ACI_A)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_onlyPniMatches() {
    val inputId: RecipientId = insert(null, PNI_A, null)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_pniAndAciMatches() {
    val inputId: RecipientId = insert(null, PNI_A, ACI_A)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  @Test
  fun processCdsV2Result_onlyAciMatches() {
    val inputId: RecipientId = insert(null, null, ACI_A)
    val resultId: RecipientId = recipientDatabase.processCdsV2Result(E164_A, PNI_A, ACI_A)

    val record: IdRecord = require(resultId)

    assertEquals(inputId, record.id)
    assertEquals(E164_A, record.e164)
    assertEquals(ACI_A, record.sid)
    assertEquals(PNI_A, record.pni)
  }

  private fun insert(e164: String?, pni: PNI?, aci: ACI?): RecipientId {
    val id: Long = SignalDatabase.rawDatabase.insert(
      RecipientDatabase.TABLE_NAME,
      null,
      contentValuesOf(
        RecipientDatabase.PHONE to e164,
        RecipientDatabase.SERVICE_ID to (aci ?: pni)?.toString(),
        RecipientDatabase.PNI_COLUMN to pni?.toString(),
        RecipientDatabase.REGISTERED to RecipientDatabase.RegisteredState.REGISTERED.id
      )
    )

    return RecipientId.from(id)
  }

  private fun require(id: RecipientId): IdRecord {
    return get(id)!!
  }

  private fun get(id: RecipientId): IdRecord? {
    SignalDatabase.rawDatabase
      .select(RecipientDatabase.ID, RecipientDatabase.PHONE, RecipientDatabase.SERVICE_ID, RecipientDatabase.PNI_COLUMN)
      .from(RecipientDatabase.TABLE_NAME)
      .where("${RecipientDatabase.ID} = ?", id)
      .run()
      .use { cursor ->
        return if (cursor.moveToFirst()) {
          IdRecord(
            id = RecipientId.from(cursor.requireLong(RecipientDatabase.ID)),
            e164 = cursor.requireString(RecipientDatabase.PHONE),
            sid = ServiceId.parseOrNull(cursor.requireString(RecipientDatabase.SERVICE_ID)),
            pni = PNI.parseOrNull(cursor.requireString(RecipientDatabase.PNI_COLUMN))
          )
        } else {
          null
        }
      }
  }

  private fun ensureDbEmpty() {
    SignalDatabase.rawDatabase.rawQuery("SELECT COUNT(*) FROM ${RecipientDatabase.TABLE_NAME} WHERE ${RecipientDatabase.DISTRIBUTION_LIST_ID} IS NULL ", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getLong(0))
    }
  }

  private data class IdRecord(
    val id: RecipientId,
    val e164: String?,
    val sid: ServiceId?,
    val pni: PNI?,
  )

  companion object {
    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val PNI_A = PNI.from(UUID.fromString("154b8d92-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("ba92b1fb-cd55-40bf-adda-c35a85375533"))

    const val E164_A = "+12221234567"
    const val E164_B = "+13331234567"
  }
}
