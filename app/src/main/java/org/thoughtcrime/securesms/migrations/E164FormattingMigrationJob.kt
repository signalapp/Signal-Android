package org.thoughtcrime.securesms.migrations

import android.database.sqlite.SQLiteException
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
import org.thoughtcrime.securesms.util.SignalE164Util
import java.util.Optional

/**
 * Through testing, we've discovered that there are some duplicate E164's in the database. They're not identical strings, since the UNIQUE constraint
 * would prevent that, but when mapped to a uint64, they become identical.
 *
 * We've gathered evidence that suggests that the issue is inconsistent E164 formatting. So, this migration runs through all of the saved E164's and checks
 * their validity. In situations where there's dupes, we merge. In situations where the number is completely invalid, we attempt to remove it.
 *
 * Normally we'd do something like this in a DB migration, but we wanted to have access to recipient merging and number formatting, which could
 * be unnecessarily difficult in a DB migration.
 */
internal class E164FormattingMigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(E164FormattingMigrationJob::class.java)
    const val KEY = "E164FormattingMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val stopwatch = Stopwatch("format-e164")

    val e164sByRecipientId = SignalDatabase.recipients.getAllE164sByRecipientId()
    val invalidE164s: MutableList<RecipientId> = mutableListOf()
    stopwatch.split("fetch")

    for ((id, e164) in e164sByRecipientId) {
      val formattedE164: String? = SignalE164Util.formatAsE164(e164)
      if (formattedE164 == null) {
        invalidE164s.add(id)
        continue
      }

      if (formattedE164 == e164) {
        continue
      }

      try {
        Log.w(TAG, "Formatted e164 did not match saved e164. Attempting to update...")
        SignalDatabase.recipients.setE164(id, formattedE164)
        Log.w(TAG, "Successfully updated without a conflict.")
      } catch (e: SQLiteException) {
        Log.w(TAG, "Hit a SQLiteException, likely a conflict. Looking to merge.", true)

        val existing: Optional<RecipientId> = SignalDatabase.recipients.getByE164(formattedE164)
        if (existing.isPresent) {
          Log.w(TAG, "Merging ${existing.get()} and $id", true)
          SignalDatabase.rawDatabase.withinTransaction {
            SignalDatabase.recipients.mergeForMigration(existing.get(), id)
          }
          Log.w(TAG, "Successfully merged ${existing.get()} and $id", true)
        } else {
          Log.w(TAG, "Unable to set E164, and it's not a conflict? Crashing.", e)
          throw e
        }
      }
    }
    stopwatch.split("format")

    if (invalidE164s.isNotEmpty()) {
      Log.w(TAG, "There were ${invalidE164s.size} invalid numbers found that could not be converted to proper e164's at all. Attempting to remove them.", true)
      val failureCount = attemptToGetRidOfE164(invalidE164s)
      if (failureCount > 0) {
        Log.w(TAG, "Failed to remove $failureCount of the ${invalidE164s.size} invalid numbers.", true)
      } else {
        Log.w(TAG, "Successfully removed all ${invalidE164s.size} invalid numbers.", true)
      }
    } else {
      Log.w(TAG, "No invalid E164's found. All could be formatted as e164.")
    }
    stopwatch.split("invalid")

    stopwatch.stop(TAG)
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun attemptToGetRidOfE164(entries: List<RecipientId>): Int {
    var failureCount = 0

    for (id in entries) {
      if (SignalDatabase.recipients.removeE164IfAnotherIdentifierIsPresent(id)) {
        Log.run { w(TAG, "[$id] Successfully removed an invalid e164 on a recipient that has other identifiers.", true) }
        continue
      }
      Log.w(TAG, "[$id] Unable to remove an invalid e164 on a recipient because it has no other identifiers. Attempting to remove the recipient entirely.", true)

      if (SignalDatabase.recipients.deleteRecipientIfItHasNoMessages(id)) {
        Log.w(TAG, "[$id] Successfully deleted a recipient with an invalid e164 because it had no messages.", true)
        continue
      }
      Log.w(TAG, "[$id] Unable to delete a recipient with an invalid e164 because it had messages.", true)
      failureCount++
    }

    return failureCount
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

  private fun RecipientTable.setE164(id: RecipientId, e164: String) {
    writableDatabase
      .update(TABLE_NAME)
      .values(E164 to e164)
      .where("$ID = ?", id)
      .run()
  }

  class Factory : Job.Factory<E164FormattingMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): E164FormattingMigrationJob {
      return E164FormattingMigrationJob(parameters)
    }
  }
}
