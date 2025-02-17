package org.thoughtcrime.securesms.migrations

import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToMap
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.update
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.RecipientTable.Companion.ACI_COLUMN
import org.thoughtcrime.securesms.database.RecipientTable.Companion.E164
import org.thoughtcrime.securesms.database.RecipientTable.Companion.ID
import org.thoughtcrime.securesms.database.RecipientTable.Companion.PNI_COLUMN
import org.thoughtcrime.securesms.database.RecipientTable.Companion.TABLE_NAME
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Through testing, we've discovered that there are some duplicate E164's in the database. They're not identical strings, since the UNIQUE constraint
 * would prevent that, but when mapped to a uint64, they become identical.
 *
 * The running theory is that there is likely a 0-prefix on some E164's that would cause two numbers to be identical when converted to a uint64.
 * So we try to find the dupes, clean them up, and in the worst case, merge the two recipients together.
 *
 * This is very similar to [BadE164MigrationJob] and re-uses a lot of code (but copies it, rather than DRY's it, to keep the migrations isolated).
 *
 * Normally we'd do something like this in a DB migration, but we wanted to have access to recipient merging and number formatting, which could
 * be unnecessarily difficult in a DB migration.
 */
internal class DuplicateE164MigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(DuplicateE164MigrationJob::class.java)
    const val KEY = "DuplicateE164MigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val stopwatch = Stopwatch("dupe-e164")

    val e164sByRecipientId = SignalDatabase.recipients.getAllE164sByRecipientId()
    val entriesByUint: MutableMap<Long, MutableList<E164Entry>> = mutableMapOf()
    val invalidE164s: MutableList<E164Entry> = mutableListOf()
    stopwatch.split("fetch")

    for ((id, e164) in e164sByRecipientId) {
      val entry = E164Entry(
        id = id,
        e164 = e164
      )

      val e164Uint = e164.convertToLong()
      if (e164Uint == null) {
        Log.w(TAG, "[$id] Found an e164 that was inconvertible to a uint64!")
        invalidE164s += entry
        continue
      }

      val existing = entriesByUint.computeIfAbsent(e164Uint) { mutableListOf() }
      existing += entry
    }
    stopwatch.split("convert")

    if (invalidE164s.isNotEmpty()) {
      Log.w(TAG, "There were ${invalidE164s.size} invalid E164's found that could not be converted to a uint64 at all. Attempting to remove them.")
      val remainder = attemptToGetRidOfE164(invalidE164s)
      if (remainder.isNotEmpty()) {
        Log.w(TAG, "There are still ${remainder.size}/${invalidE164s.size} invalid entries. We'll have to live with them.")
      }
    } else {
      Log.w(TAG, "No invalid E164's found. All could be represented as a uint64.")
    }
    stopwatch.split("invalid")

    val dupes = entriesByUint.filter { it.value.size > 1 }
    if (dupes.isEmpty()) {
      Log.i(TAG, "No duplicate entries. No action needed!")
      return
    }

    Log.w(TAG, "Found ${dupes.size} unique E164 uint64s that have multiple string mappings. Attempting to repair.")

    for ((_, entries) in dupes) {
      val resolved = attemptToResolveConflict(entries)

      if (resolved.size <= 1) {
        Log.w(TAG, "Successfully resolved conflicts for this batch.")
        continue
      }

      Log.w(TAG, "Was not able to resolve all conflicts. We must merge the contacts together.")
      SignalDatabase.rawDatabase.withinTransaction {
        val first = resolved.first()
        for (entry in resolved.drop(1)) {
          Log.w(TAG, "Merging ${first.id} with ${entry.id}")
          SignalDatabase.recipients.mergeForMigration(first.id, entry.id)
        }
      }
    }

    stopwatch.split("resolve")
    stopwatch.stop(TAG)
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun attemptToResolveConflict(entries: List<E164Entry>): List<E164Entry> {
    val invalidPrefixes = entries.filter { it.e164.startsWith("0") || it.e164.startsWith("+0") || it.e164.startsWith("++") }
    if (invalidPrefixes.isEmpty()) {
      Log.w(TAG, "No entries with invalid prefixes, and therefore no evidence as to which duplicate entries would be worth removing.")
      return entries
    }

    Log.w(TAG, "Found that ${invalidPrefixes.size}/${entries.size} entries had an invalid prefix. Attempting to strip the e164.")

    return attemptToGetRidOfE164(entries)
  }

  private fun attemptToGetRidOfE164(entries: List<E164Entry>): List<E164Entry> {
    val out: MutableList<E164Entry> = entries.toMutableList()

    for (invalidPrefix in entries) {
      if (SignalDatabase.recipients.removeE164IfAnotherIdentifierIsPresent(invalidPrefix.id)) {
        Log.w(TAG, "[${invalidPrefix.id}] Successfully removed a conflicting e164 on a recipient that has other identifiers.")
        out.remove(invalidPrefix)
        continue
      }
      Log.w(TAG, "[${invalidPrefix.id}] Unable to remove a conflicting e164 on a recipient because it has no other identifiers. Attempting to remove the recipient entirely.")

      if (SignalDatabase.recipients.deleteRecipientIfItHasNoMessages(invalidPrefix.id)) {
        Log.w(TAG, "[${invalidPrefix.id}] Successfully deleted a recipient with a conflicting e164 because it had no messages.")
        out.remove(invalidPrefix)
        continue
      }
      Log.w(TAG, "[${invalidPrefix.id}] Unable to deleted a recipient with a conflicting e164 because it had messages.")
    }

    return out
  }

  private fun RecipientTable.removeE164IfAnotherIdentifierIsPresent(recipientId: RecipientId): Boolean {
    return readableDatabase
      .update(TABLE_NAME)
      .values(E164 to null)
      .where("$ID = ? AND ($ACI_COLUMN NOT NULL OR $PNI_COLUMN NOT NULL)", recipientId)
      .run() > 0
  }

  private fun RecipientTable.deleteRecipientIfItHasNoMessages(recipientId: RecipientId): Boolean {
    return readableDatabase
      .delete(TABLE_NAME)
      .where(
        """
        $ID = ? AND
        $ID NOT IN (
          SELECT ${MessageTable.TO_RECIPIENT_ID} FROM ${MessageTable.TABLE_NAME}
          UNION
          SELECT ${MessageTable.FROM_RECIPIENT_ID} FROM ${MessageTable.TABLE_NAME}
        )
        """,
        recipientId
      )
      .run() > 0
  }

  private fun RecipientTable.getAllE164sByRecipientId(): Map<RecipientId, String> {
    return readableDatabase
      .select(ID, E164)
      .from(TABLE_NAME)
      .where("$E164 NOT NULL")
      .run()
      .readToMap {
        RecipientId.from(it.requireLong(ID)) to it.requireNonNullString(E164)
      }
  }

  private fun String.convertToLong(): Long? {
    val fixed = if (this.startsWith("+")) {
      this.substring(1)
    } else {
      this
    }

    return fixed.toLongOrNull()
  }

  private data class E164Entry(
    val id: RecipientId,
    val e164: String
  )

  class Factory : Job.Factory<DuplicateE164MigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DuplicateE164MigrationJob {
      return DuplicateE164MigrationJob(parameters)
    }
  }
}
