package org.signal.smsexporter.internal.mms

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import android.provider.Telephony
import com.klinker.android.send_message.Utils
import org.signal.core.util.Try
import org.signal.core.util.logging.Log
import org.signal.smsexporter.ExportableMessage

/**
 * Given a list of messages, gets or creates the threadIds for each different recipient set.
 * Returns a list of outputs that tie a given message to a thread id.
 *
 * This method will also filter out messages that do not have addresses.
 */
internal object GetOrCreateMmsThreadIdsUseCase {

  private val TAG = Log.tag(GetOrCreateMmsThreadIdsUseCase::class.java)

  fun execute(
    context: Context,
    mms: ExportableMessage.Mms<*>,
    threadCache: MutableMap<Set<String>, Long>
  ): Try<Output> {
    return try {
      val recipients = getRecipientSet(mms)
      val threadId = getOrCreateThreadId(context, recipients, threadCache)

      Try.success(Output(mms, threadId))
    } catch (e: Exception) {
      Try.failure(e)
    }
  }

  private fun getOrCreateThreadId(context: Context, recipients: Set<String>, cache: MutableMap<Set<String>, Long>): Long {
    return if (cache.containsKey(recipients)) {
      cache[recipients]!!
    } else {
      val threadId = try {
        Utils.getOrCreateThreadId(context, recipients)
      } catch (e: SQLiteException) {
        Log.w(TAG, "Unable to create thread using Klinker, falling back to system if possible")
        if (Build.VERSION.SDK_INT >= 23) {
          Telephony.Threads.getOrCreateThreadId(context, recipients)
        } else {
          throw e
        }
      }
      cache[recipients] = threadId
      threadId
    }
  }

  private fun getRecipientSet(mms: ExportableMessage.Mms<*>): Set<String> {
    val recipients = mms.addresses
    if (recipients.isEmpty()) {
      error("Expected non-empty recipient count.")
    }

    return HashSet(recipients.map { it })
  }

  data class Output(val mms: ExportableMessage.Mms<*>, val threadId: Long)
}
