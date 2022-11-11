package org.thoughtcrime.securesms.database

import androidx.core.content.contentValuesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.signal.core.util.select
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage
import org.thoughtcrime.securesms.sms.IncomingTextMessage
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientDatabaseTest_processPnpTuple {

  private lateinit var recipientDatabase: RecipientDatabase
  private lateinit var smsDatabase: SmsDatabase
  private lateinit var threadDatabase: ThreadDatabase

  private val localAci = ACI.from(UUID.randomUUID())
  private val localPni = PNI.from(UUID.randomUUID())

  @Before
  fun setup() {
    recipientDatabase = SignalDatabase.recipients
    smsDatabase = SignalDatabase.sms
    threadDatabase = SignalDatabase.threads

    ensureDbEmpty()

    SignalStore.account().setAci(localAci)
    SignalStore.account().setPni(localPni)
  }

  @Test
  fun noMatch_e164Only() {
    test {
      process(E164_A, null, null)
      expect(E164_A, null, null)
    }
  }

  @Test
  fun noMatch_e164AndPni() {
    test {
      process(E164_A, PNI_A, null)
      expect(E164_A, PNI_A, null)
    }
  }

  @Test
  fun noMatch_aciOnly() {
    test {
      process(null, null, ACI_A)
      expect(null, null, ACI_A)
    }
  }

  @Test(expected = IllegalStateException::class)
  fun noMatch_noData() {
    test {
      process(null, null, null)
    }
  }

  @Test
  fun noMatch_allFields() {
    test {
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun fullMatch() {
    test {
      given(E164_A, PNI_A, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyE164Matches() {
    test {
      given(E164_A, null, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyE164Matches_differentAci() {
    test {
      given(E164_A, null, ACI_B)
      process(E164_A, PNI_A, ACI_A)

      expect(null, null, ACI_B)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun e164AndPniMatches() {
    test {
      given(E164_A, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun e164AndAciMatches() {
    test {
      given(E164_A, null, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyPniMatches() {
    test {
      given(null, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun pniAndAciMatches() {
    test {
      given(null, PNI_A, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyAciMatches() {
    test {
      given(null, null, ACI_A)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyE164Matches_pniChanges_noAciProvided_noPniSession() {
    test {
      given(E164_A, PNI_B, null)
      process(E164_A, PNI_A, null)
      expect(E164_A, PNI_A, null)
    }
  }

  @Test
  fun e164AndPniMatches_noExistingSession() {
    test {
      given(E164_A, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyPniMatches_noExistingSession() {
    test {
      given(null, PNI_A, null)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun onlyPniMatches_noExistingPniSession_changeNumber() {
    // This test, I could go either way. We decide to change the E164 on the existing row rather than create a new one.
    // But it's an "unstable E164->PNI mapping" case, which we don't expect, so as long as there's a user-visible impact that should be fine.
    test {
      given(E164_B, PNI_A, null, createThread = true)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }
  }

  @Test
  fun pniAndAciMatches_changeNumber() {
    // This test, I could go either way. We decide to change the E164 on the existing row rather than create a new one.
    // But it's an "unstable E164->PNI mapping" case, which we don't expect, so as long as there's a user-visible impact that should be fine.
    test {
      given(E164_B, PNI_A, ACI_A, createThread = true)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }
  }

  @Test
  fun onlyAciMatches_changeNumber() {
    test {
      given(E164_B, null, ACI_A, createThread = true)
      process(E164_A, PNI_A, ACI_A)
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }
  }

  @Test
  fun merge_e164Only_pniOnly_aciOnly() {
    test {
      given(E164_A, null, null)
      given(null, PNI_A, null)
      given(null, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun merge_e164Only_pniOnly_noAciProvided() {
    test {
      given(E164_A, null, null)
      given(null, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expectDeleted()
    }
  }

  @Test
  fun merge_e164Only_pniOnly_aciProvidedButNoAciRecord() {
    test {
      given(E164_A, null, null)
      given(null, PNI_A, null)

      process(E164_A, PNI_A, ACI_A)

      expect(E164_A, PNI_A, ACI_A)
      expectDeleted()
    }
  }

  @Test
  fun merge_e164Only_pniAndE164_noAciProvided() {
    test {
      given(E164_A, null, null)
      given(E164_B, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expect(E164_B, null, null)
    }
  }

  @Test
  fun merge_e164AndPni_pniOnly_noAciProvided() {
    test {
      given(E164_A, PNI_B, null)
      given(null, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expectDeleted()
    }
  }

  @Test
  fun merge_e164AndPni_e164AndPni_noAciProvided_noSessions() {
    test {
      given(E164_A, PNI_B, null)
      given(E164_B, PNI_A, null)

      process(E164_A, PNI_A, null)

      expect(E164_A, PNI_A, null)
      expect(E164_B, null, null)
    }
  }

  @Test
  fun merge_e164AndPni_aciOnly() {
    test {
      given(E164_A, PNI_A, null)
      given(null, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun merge_e164AndPni_aciOnly_e164RecordHasSeparateE164() {
    test {
      given(E164_B, PNI_A, null)
      given(null, null, ACI_A)

      process(E164_A, PNI_A, ACI_A)

      expect(E164_B, null, null)
      expect(E164_A, PNI_A, ACI_A)
    }
  }

  @Test
  fun merge_e164AndPni_e164AndPniAndAci_changeNumber() {
    test {
      given(E164_A, PNI_A, null)
      given(E164_B, PNI_B, ACI_A, createThread = true)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }
  }

  @Test
  fun merge_e164AndPni_e164Aci_changeNumber() {
    test {
      given(E164_A, PNI_A, null)
      given(E164_B, null, ACI_A, createThread = true)

      process(E164_A, PNI_A, ACI_A)

      expectDeleted()
      expect(E164_A, PNI_A, ACI_A)

      expectChangeNumberEvent()
    }
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

  /**
   * Baby DSL for making tests readable.
   */
  private fun test(init: TestCase.() -> Unit): TestCase {
    val test = TestCase()
    test.init()
    return test
  }

  private inner class TestCase {
    private val generatedIds: LinkedHashSet<RecipientId> = LinkedHashSet()
    private var expectCount = 0
    private lateinit var outputRecipientId: RecipientId

    fun given(e164: String?, pni: PNI?, aci: ACI?, createThread: Boolean = false) {
      val id = insert(e164, pni, aci)
      generatedIds += id
      if (createThread) {
        // Create a thread and throw a dummy message in it so it doesn't get automatically deleted
        threadDatabase.getOrCreateThreadIdFor(Recipient.resolved(id))
        smsDatabase.insertMessageInbox(IncomingEncryptedMessage(IncomingTextMessage(id, 1, 0, 0, 0, "", Optional.empty(), 0, false, ""), ""))
      }
    }

    fun process(e164: String?, pni: PNI?, aci: ACI?) {
      SignalDatabase.rawDatabase.beginTransaction()
      try {
        outputRecipientId = recipientDatabase.processPnpTuple(e164, pni, aci, pniVerified = false).finalId
        generatedIds += outputRecipientId
        SignalDatabase.rawDatabase.setTransactionSuccessful()
      } finally {
        SignalDatabase.rawDatabase.endTransaction()
      }
    }

    fun expect(e164: String?, pni: PNI?, aci: ACI?) {
      expect(generatedIds.elementAt(expectCount++), e164, pni, aci)
    }

    fun expect(id: RecipientId, e164: String?, pni: PNI?, aci: ACI?) {
      val record: IdRecord = require(id)
      assertEquals(e164, record.e164)
      assertEquals(pni, record.pni)
      assertEquals(aci ?: pni, record.sid)
    }

    fun expectDeleted() {
      expectDeleted(generatedIds.elementAt(expectCount++))
    }

    fun expectDeleted(id: RecipientId) {
      assertNull(get(id))
    }

    fun expectChangeNumberEvent() {
      assertEquals(1, smsDatabase.getChangeNumberMessageCount(outputRecipientId))
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
