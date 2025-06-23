package org.thoughtcrime.securesms.migrations

import android.database.sqlite.SQLiteConstraintException
import org.signal.core.util.Stopwatch
import org.signal.core.util.delete
import org.signal.core.util.logging.Log
import org.signal.core.util.readToList
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

/**
 * Through testing, we've discovered that some badly-formatted e164's wound up in the e164 column of the recipient table.
 * We believe this was mostly a result of a legacy chat-creation path where we'd basically create a recipient with any number that was input.
 * This makes a best-effort to clear out that bad data in a safe manner.
 * Normally we'd do something like this in a DB migration, but we wanted to have access to recipient merging and number formatting, which could
 * be unnecessarily difficult in a DB migration.
 */
internal class BadE164MigrationJob(
  parameters: Parameters = Parameters.Builder().build()
) : MigrationJob(parameters) {

  companion object {
    val TAG = Log.tag(BadE164MigrationJob::class.java)
    const val KEY = "BadE164MigrationJob"

    /**
     * Accounts for the following types of invalid numbers:
     * - Contains an invalid char anywhere (i.e. not a digit or +)
     * - A shortcode that doesn't start with a number
     * - A non-shortcode (longcode?) that doesn't start with +{digit}
     * - A number with exactly 7 chars (strange but true -- neither shortcodes nor longcodes can be 7 chars long)
     */
    private const val INVALID_E164_QUERY =
      """
      (
        $E164 NOT NULL AND (
          $E164 GLOB '*[^+0-9]*' 
            OR (LENGTH($E164) > 7 AND $E164 NOT GLOB '+[0-9]*')
            OR (LENGTH($E164) == 7)
            OR (LENGTH($E164) < 7 AND $E164 NOT GLOB '[0-9]*')
        )
      )
      """
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val stopwatch = Stopwatch("bad-e164")

    val invalidWithOtherIdentifiersCount = SignalDatabase.recipients.removeInvalidE164sFromRecipientsWithOtherIdentifiers()
    Log.d(TAG, "Removed $invalidWithOtherIdentifiersCount invalid e164's from recipients that had another identifier.")
    stopwatch.split("alt-id")

    val invalidWithNoMessagesCount = SignalDatabase.recipients.deleteRecipientsWithInvalidE164sAndNoMessages()
    Log.d(TAG, "Deleted $invalidWithNoMessagesCount recipients with invalid e164's and no messages.")
    stopwatch.split("no-msg")

    val invalidEntries = SignalDatabase.recipients.getRecipientsWithInvalidE164s()
    stopwatch.split("fetch-invalid")
    if (invalidEntries.isEmpty()) {
      Log.d(TAG, "No more invalid e164s, we're done.")
      stopwatch.stop(TAG)
      return
    }

    Log.i(TAG, "There are ${invalidEntries.size} remaining entries ")

    val hasLettersRegex = "[^+0-9]".toRegex()
    for (entry in invalidEntries) {
      if (entry.e164.matches(hasLettersRegex)) {
        Log.w(TAG, "We have encountered an e164-only recipient, with messages, that has invalid characters in the e164.")
        continue
      }

      val formattedNumber = SignalE164Util.formatAsE164(entry.e164)
      if (formattedNumber == null) {
        Log.w(TAG, "We have encountered an e164-only recipient, with messages, whose value characters are all valid, but still remains completely unparsable.")
        continue
      }

      if (formattedNumber == entry.e164) {
        Log.w(TAG, "We have encountered an e164-only recipient, with messages, whose value characters are all valid, but after formatting the number, it's the same!")
        continue
      }

      SignalDatabase.recipients.updateE164(entry.id, entry.e164)
      Log.w(TAG, "Update the E164 to the new, formatted number.")
    }

    stopwatch.split("merge")
    stopwatch.stop(TAG)
  }

  override fun shouldRetry(e: Exception): Boolean = false

  private fun RecipientTable.removeInvalidE164sFromRecipientsWithOtherIdentifiers(): Int {
    return readableDatabase
      .update(TABLE_NAME)
      .values(E164 to null)
      .where("$INVALID_E164_QUERY AND ($ACI_COLUMN NOT NULL OR $PNI_COLUMN NOT NULL)")
      .run()
  }

  private fun RecipientTable.deleteRecipientsWithInvalidE164sAndNoMessages(): Int {
    return readableDatabase
      .delete(TABLE_NAME)
      .where(
        """
        $INVALID_E164_QUERY AND
        $ID NOT IN (
          SELECT ${MessageTable.TO_RECIPIENT_ID} FROM ${MessageTable.TABLE_NAME}
          UNION
          SELECT ${MessageTable.FROM_RECIPIENT_ID} FROM ${MessageTable.TABLE_NAME}
        )
        """
      )
      .run()
  }

  private fun RecipientTable.getRecipientsWithInvalidE164s(): List<InvalidEntry> {
    return readableDatabase
      .select(ID, E164)
      .from(TABLE_NAME)
      .where(INVALID_E164_QUERY)
      .run()
      .readToList {
        InvalidEntry(
          id = it.requireLong(ID),
          e164 = it.requireNonNullString(E164)
        )
      }
  }

  private fun RecipientTable.updateE164(id: Long, e164: String) {
    writableDatabase.withinTransaction { db ->
      try {
        db.update(TABLE_NAME)
          .values(E164 to e164)
          .where("$ID = ?", id)
          .run()
      } catch (e: SQLiteConstraintException) {
        Log.w(TAG, "There was a conflict when trying to update Recipient::$id with a properly-formatted E164. Merging.")
        val existing = getByE164(e164).get()
        mergeForMigration(existing, RecipientId.from(id))
      }
    }
  }

  private data class InvalidEntry(
    val id: Long,
    val e164: String
  )

  class Factory : Job.Factory<BadE164MigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): BadE164MigrationJob {
      return BadE164MigrationJob(parameters)
    }
  }
}
