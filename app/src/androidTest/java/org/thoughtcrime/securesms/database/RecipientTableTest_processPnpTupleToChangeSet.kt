package org.thoughtcrime.securesms.database

import androidx.core.content.contentValuesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalDatabaseRule
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.push.ServiceId
import java.lang.AssertionError
import java.lang.IllegalStateException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipientTableTest_processPnpTupleToChangeSet {

  @Rule
  @JvmField
  val databaseRule = SignalDatabaseRule(deleteAllThreadsOnEachRun = false)

  private lateinit var db: RecipientTable

  @Before
  fun setup() {
    db = SignalDatabase.recipients
  }

  @Test
  fun noMatch_e164Only() {
    val changeSet = db.processPnpTupleToChangeSet(E164_A, null, null, pniVerified = false)

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpInsert(E164_A, null, null)
      ),
      changeSet
    )
  }

  @Test
  fun noMatch_e164AndPni() {
    val changeSet = db.processPnpTupleToChangeSet(E164_A, PNI_A, null, pniVerified = false)

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpInsert(E164_A, PNI_A, null)
      ),
      changeSet
    )
  }

  @Test
  fun noMatch_aciOnly() {
    val changeSet = db.processPnpTupleToChangeSet(null, null, ACI_A, pniVerified = false)

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpInsert(null, null, ACI_A)
      ),
      changeSet
    )
  }

  @Test(expected = IllegalStateException::class)
  fun noMatch_noData() {
    db.processPnpTupleToChangeSet(null, null, null, pniVerified = false)
  }

  @Test
  fun noMatch_allFields() {
    val changeSet = db.processPnpTupleToChangeSet(E164_A, PNI_A, ACI_A, pniVerified = false)

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpInsert(E164_A, PNI_A, ACI_A)
      ),
      changeSet
    )
  }

  @Test
  fun fullMatch() {
    val result = applyAndAssert(
      Input(E164_A, PNI_A, ACI_A),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id)
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyE164Matches() {
    val result = applyAndAssert(
      Input(E164_A, null, null),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetPni(result.id, PNI_A),
          PnpOperation.SetAci(result.id, ACI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyE164Matches_pniChanges_noAciProvided_existingPniSession() {
    val result = applyAndAssert(
      Input(E164_A, PNI_B, null, pniSession = true),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetPni(result.id, PNI_A),
          PnpOperation.SessionSwitchoverInsert(result.id)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyE164Matches_pniChanges_noAciProvided_noPniSession() {
    val result = applyAndAssert(
      Input(E164_A, PNI_B, null),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetPni(result.id, PNI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun e164AndPniMatches_noExistingSession() {
    val result = applyAndAssert(
      Input(E164_A, PNI_A, null),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetAci(result.id, ACI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun e164AndPniMatches_existingPniSession() {
    val result = applyAndAssert(
      Input(E164_A, PNI_A, null, pniSession = true),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetAci(result.id, ACI_A),
          PnpOperation.SessionSwitchoverInsert(result.id)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun e164AndAciMatches() {
    val result = applyAndAssert(
      Input(E164_A, null, ACI_A),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetPni(result.id, PNI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyPniMatches_noExistingSession() {
    val result = applyAndAssert(
      Input(null, PNI_A, null),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
          PnpOperation.SetAci(result.id, ACI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyPniMatches_existingPniSession() {
    val result = applyAndAssert(
      Input(null, PNI_A, null, pniSession = true),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
          PnpOperation.SetAci(result.id, ACI_A),
          PnpOperation.SessionSwitchoverInsert(result.id)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyPniMatches_existingPniSession_changeNumber() {
    val result = applyAndAssert(
      Input(E164_B, PNI_A, null, pniSession = true),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
          PnpOperation.SetAci(result.id, ACI_A),
          PnpOperation.ChangeNumberInsert(
            recipientId = result.id,
            oldE164 = E164_B,
            newE164 = E164_A
          ),
          PnpOperation.SessionSwitchoverInsert(result.id)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun pniAndAciMatches() {
    val result = applyAndAssert(
      Input(null, PNI_A, ACI_A),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun pniAndAciMatches_changeNumber() {
    val result = applyAndAssert(
      Input(E164_B, PNI_A, ACI_A),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
          PnpOperation.ChangeNumberInsert(
            recipientId = result.id,
            oldE164 = E164_B,
            newE164 = E164_A
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyAciMatches() {
    val result = applyAndAssert(
      Input(null, null, ACI_A),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
          PnpOperation.SetPni(result.id, PNI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun onlyAciMatches_changeNumber() {
    val result = applyAndAssert(
      Input(E164_B, null, ACI_A),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.id),
        operations = listOf(
          PnpOperation.SetE164(result.id, E164_A),
          PnpOperation.SetPni(result.id, PNI_A),
          PnpOperation.ChangeNumberInsert(
            recipientId = result.id,
            oldE164 = E164_B,
            newE164 = E164_A
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164Only_pniOnly_aciOnly() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, null, null),
        Input(null, PNI_A, null),
        Input(null, null, ACI_A)
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.thirdId),
        operations = listOf(
          PnpOperation.Merge(
            primaryId = result.firstId,
            secondaryId = result.secondId
          ),
          PnpOperation.Merge(
            primaryId = result.thirdId,
            secondaryId = result.firstId
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164Only_pniOnly_noAciProvided() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, null, null),
        Input(null, PNI_A, null),
      ),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.firstId),
        operations = listOf(
          PnpOperation.Merge(
            primaryId = result.firstId,
            secondaryId = result.secondId
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164Only_pniOnly_aciProvidedButNoAciRecord() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, null, null),
        Input(null, PNI_A, null),
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.firstId),
        operations = listOf(
          PnpOperation.Merge(
            primaryId = result.firstId,
            secondaryId = result.secondId
          ),
          PnpOperation.SetAci(
            recipientId = result.firstId,
            aci = ACI_A
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164Only_pniAndE164_noAciProvided() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, null, null),
        Input(E164_B, PNI_A, null),
      ),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.firstId),
        operations = listOf(
          PnpOperation.RemovePni(result.secondId),
          PnpOperation.SetPni(
            recipientId = result.firstId,
            pni = PNI_A
          ),
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_pniOnly_noAciProvided() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, PNI_B, null),
        Input(null, PNI_A, null),
      ),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.firstId),
        operations = listOf(
          PnpOperation.RemovePni(result.firstId),
          PnpOperation.Merge(
            primaryId = result.firstId,
            secondaryId = result.secondId
          ),
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_e164AndPni_noAciProvided_noSessions() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, PNI_B, null),
        Input(E164_B, PNI_A, null),
      ),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.firstId),
        operations = listOf(
          PnpOperation.RemovePni(result.secondId),
          PnpOperation.SetPni(result.firstId, PNI_A)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_e164AndPni_noAciProvided_sessionsExist() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, PNI_B, null, pniSession = true),
        Input(E164_B, PNI_A, null, pniSession = true),
      ),
      Update(E164_A, PNI_A, null),
      Output(E164_A, PNI_A, null)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.firstId),
        operations = listOf(
          PnpOperation.RemovePni(result.secondId),
          PnpOperation.SetPni(result.firstId, PNI_A),
          PnpOperation.SessionSwitchoverInsert(result.secondId),
          PnpOperation.SessionSwitchoverInsert(result.firstId)
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_aciOnly() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, PNI_A, null),
        Input(null, null, ACI_A),
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.secondId),
        operations = listOf(
          PnpOperation.Merge(
            primaryId = result.secondId,
            secondaryId = result.firstId
          ),
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_aciOnly_e164RecordHasSeparateE164() {
    val result = applyAndAssert(
      listOf(
        Input(E164_B, PNI_A, null),
        Input(null, null, ACI_A),
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.secondId),
        operations = listOf(
          PnpOperation.RemovePni(result.firstId),
          PnpOperation.SetPni(
            recipientId = result.secondId,
            pni = PNI_A,
          ),
          PnpOperation.SetE164(
            recipientId = result.secondId,
            e164 = E164_A,
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_aciOnly_e164RecordHasSeparateE164_changeNumber() {
    val result = applyAndAssert(
      listOf(
        Input(E164_B, PNI_A, null),
        Input(E164_C, null, ACI_A),
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.secondId),
        operations = listOf(
          PnpOperation.RemovePni(result.firstId),
          PnpOperation.SetPni(
            recipientId = result.secondId,
            pni = PNI_A,
          ),
          PnpOperation.SetE164(
            recipientId = result.secondId,
            e164 = E164_A,
          ),
          PnpOperation.ChangeNumberInsert(
            recipientId = result.secondId,
            oldE164 = E164_C,
            newE164 = E164_A
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_e164AndPniAndAci_changeNumber() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, PNI_A, null),
        Input(E164_B, PNI_B, ACI_A),
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.secondId),
        operations = listOf(
          PnpOperation.RemovePni(result.secondId),
          PnpOperation.RemoveE164(result.secondId),
          PnpOperation.Merge(
            primaryId = result.secondId,
            secondaryId = result.firstId
          ),
          PnpOperation.ChangeNumberInsert(
            recipientId = result.secondId,
            oldE164 = E164_B,
            newE164 = E164_A
          )
        )
      ),
      result.changeSet
    )
  }

  @Test
  fun merge_e164AndPni_e164Aci_changeNumber() {
    val result = applyAndAssert(
      listOf(
        Input(E164_A, PNI_A, null),
        Input(E164_B, null, ACI_A),
      ),
      Update(E164_A, PNI_A, ACI_A),
      Output(E164_A, PNI_A, ACI_A)
    )

    assertEquals(
      PnpChangeSet(
        id = PnpIdResolver.PnpNoopId(result.secondId),
        operations = listOf(
          PnpOperation.RemoveE164(result.secondId),
          PnpOperation.Merge(
            primaryId = result.secondId,
            secondaryId = result.firstId
          ),
          PnpOperation.ChangeNumberInsert(
            recipientId = result.secondId,
            oldE164 = E164_B,
            newE164 = E164_A
          )
        )
      ),
      result.changeSet
    )
  }

  private fun insert(e164: String?, pni: PNI?, aci: ACI?): RecipientId {
    val id: Long = SignalDatabase.rawDatabase.insert(
      RecipientTable.TABLE_NAME,
      null,
      contentValuesOf(
        RecipientTable.PHONE to e164,
        RecipientTable.SERVICE_ID to (aci ?: pni)?.toString(),
        RecipientTable.PNI_COLUMN to pni?.toString(),
        RecipientTable.REGISTERED to RecipientTable.RegisteredState.REGISTERED.id
      )
    )

    return RecipientId.from(id)
  }

  private fun insertMockSessionFor(account: ServiceId, address: ServiceId) {
    SignalDatabase.rawDatabase.insert(
      SessionTable.TABLE_NAME, null,
      contentValuesOf(
        SessionTable.ACCOUNT_ID to account.toString(),
        SessionTable.ADDRESS to address.toString(),
        SessionTable.DEVICE to 1,
        SessionTable.RECORD to Util.getSecretBytes(32)
      )
    )
  }

  data class Input(val e164: String?, val pni: PNI?, val aci: ACI?, val pniSession: Boolean = false, val aciSession: Boolean = false)
  data class Update(val e164: String?, val pni: PNI?, val aci: ACI?, val pniVerified: Boolean = false)
  data class Output(val e164: String?, val pni: PNI?, val aci: ACI?)
  data class PnpMatchResult(val ids: List<RecipientId>, val changeSet: PnpChangeSet) {
    val id
      get() = if (ids.size == 1) {
        ids[0]
      } else {
        throw IllegalStateException("There are multiple IDs, but you assumed 1!")
      }

    val firstId
      get() = ids[0]

    val secondId
      get() = ids[1]

    val thirdId
      get() = ids[2]
  }

  private fun applyAndAssert(input: Input, update: Update, output: Output): PnpMatchResult {
    return applyAndAssert(listOf(input), update, output)
  }

  /**
   * Helper method that will call insert your recipients, call [RecipientTable.processPnpTupleToChangeSet] with your params,
   * and then verify your output matches what you expect.
   *
   * It results the inserted ID's and changeset for additional verification.
   *
   * But basically this is here to make the tests more readable. It gives you a clear list of:
   * - input
   * - update
   * - output
   *
   * that you can spot check easily.
   *
   * Important: The output will only include records that contain fields from the input. That means
   * for:
   *
   * Input:  E164_B, PNI_A, null
   * Update: E164_A, PNI_A, null
   *
   * You will get:
   * Output: E164_A, PNI_A, null
   *
   * Even though there was an update that will also result in the row (E164_B, null, null)
   */
  private fun applyAndAssert(input: List<Input>, update: Update, output: Output): PnpMatchResult {
    val ids = input.map { insert(it.e164, it.pni, it.aci) }

    input
      .filter { it.pniSession }
      .forEach { insertMockSessionFor(databaseRule.localAci, it.pni!!) }

    input
      .filter { it.aciSession }
      .forEach { insertMockSessionFor(databaseRule.localAci, it.aci!!) }

    val byE164 = update.e164?.let { db.getByE164(it).orElse(null) }
    val byPniSid = update.pni?.let { db.getByServiceId(it).orElse(null) }
    val byAciSid = update.aci?.let { db.getByServiceId(it).orElse(null) }

    val data = PnpDataSet(
      e164 = update.e164,
      pni = update.pni,
      aci = update.aci,
      byE164 = byE164,
      byPniSid = byPniSid,
      byPniOnly = update.pni?.let { db.getByPni(it).orElse(null) },
      byAciSid = byAciSid,
      e164Record = byE164?.let { db.getRecord(it) },
      pniSidRecord = byPniSid?.let { db.getRecord(it) },
      aciSidRecord = byAciSid?.let { db.getRecord(it) }
    )
    val changeSet = db.processPnpTupleToChangeSet(update.e164, update.pni, update.aci, pniVerified = update.pniVerified)

    val finalData = data.perform(changeSet.operations)

    val finalRecords = setOfNotNull(finalData.e164Record, finalData.pniSidRecord, finalData.aciSidRecord)
    assertEquals("There's still multiple records in the resulting record set! $finalRecords", 1, finalRecords.size)

    finalRecords.firstOrNull { record -> record.e164 == output.e164 && record.pni == output.pni && record.serviceId == (output.aci ?: output.pni) }
      ?: throw AssertionError("Expected output was not found in the result set! Expected: $output")

    return PnpMatchResult(
      ids = ids,
      changeSet = changeSet
    )
  }

  companion object {
    val ACI_A = ACI.from(UUID.fromString("3436efbe-5a76-47fa-a98a-7e72c948a82e"))
    val ACI_B = ACI.from(UUID.fromString("8de7f691-0b60-4a68-9cd9-ed2f8453f9ed"))

    val PNI_A = PNI.from(UUID.fromString("154b8d92-c960-4f6c-8385-671ad2ffb999"))
    val PNI_B = PNI.from(UUID.fromString("ba92b1fb-cd55-40bf-adda-c35a85375533"))

    const val E164_A = "+12221234567"
    const val E164_B = "+13331234567"
    const val E164_C = "+14441234567"
  }
}
