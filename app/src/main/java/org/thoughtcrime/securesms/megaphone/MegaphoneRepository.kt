package org.thoughtcrime.securesms.megaphone

import android.app.Application
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.MegaphoneDatabase
import org.thoughtcrime.securesms.database.model.MegaphoneRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.days

/**
 * Synchronization of data structures is done using a serial executor. Do not access or change
 * data structures or fields on anything except the executor.
 */
class MegaphoneRepository(private val context: Application) {
  private val executor: Executor = SignalExecutors.SERIAL
  private val database: MegaphoneDatabase = MegaphoneDatabase.getInstance(context)
  private val databaseCache: MutableMap<Megaphones.Event?, MegaphoneRecord> = mutableMapOf()

  private var enabled = false

  companion object {
    private val TAG = Log.tag(MegaphoneRepository::class.java)
    private val MAX_DISPLAY_DURATION = 3.days.inWholeMilliseconds
  }

  init {
    executor.execute {
      this.init()
    }
  }

  /**
   * Marks any megaphones a new user shouldn't see as "finished".
   */
  @AnyThread
  fun onFirstEverAppLaunch() {
    executor.execute {
      database.markFinished(Megaphones.Event.ADD_A_PROFILE_PHOTO)
      database.markFinished(Megaphones.Event.PNP_LAUNCH)
      resetDatabaseCache()
    }
  }

  @AnyThread
  fun onAppForegrounded() {
    executor.execute {
      enabled = true
    }
  }

  /**
   * Note that if the next megaphone we'd choose needs to be auto-snoozed, this will result in an "off" cycle, where no megaphone will be shown.
   * We could choose to keep looking, but given that auto-snooze is intended to give the user a break from megaphones, it's probably for the best that we take
   * at least one cycle off.
   */
  @AnyThread
  fun getNextMegaphone(callback: Callback<Megaphone?>) {
    executor.execute {
      if (enabled && SignalStore.account.isRegistered) {
        init()

        val currentTime = System.currentTimeMillis()
        val next = Megaphones.getNextMegaphone(context, databaseCache)

        val isDonateMegaphone = next?.event == Megaphones.Event.REMOTE_MEGAPHONE &&
          RemoteMegaphoneRepository.getRemoteMegaphoneToShow()?.primaryActionId?.isDonateAction == true

        if (next != null && !isDonateMegaphone) {
          val record = getRecord(next.event)
          if (record.lastVisible > 0 && currentTime - record.lastVisible > MAX_DISPLAY_DURATION) {
            Log.i(TAG, "Auto-snoozing ${next.event} after being visible for ${currentTime - record.lastVisible}ms without interaction.")
            database.markInteractedWith(next.event, record.interactionCount + 1, currentTime)
            enabled = false
            resetDatabaseCache()
            callback.onResult(null)
            return@execute
          }
        }

        callback.onResult(next)
      } else {
        callback.onResult(null)
      }
    }
  }

  @AnyThread
  fun markVisible(event: Megaphones.Event) {
    val time = System.currentTimeMillis()

    executor.execute {
      val record = getRecord(event)
      var changed = false
      if (record.firstVisible == 0L) {
        database.markFirstVisible(event, time)
        changed = true
      }
      if (record.lastVisible == 0L) {
        database.markLastVisible(event, time)
        changed = true
      }
      if (changed) {
        resetDatabaseCache()
      }
    }
  }

  @AnyThread
  fun markInteractedWith(event: Megaphones.Event) {
    val currentTime = System.currentTimeMillis()

    executor.execute {
      val record = getRecord(event)
      database.markInteractedWith(event, record.interactionCount + 1, currentTime)
      enabled = false
      resetDatabaseCache()
    }
  }

  @AnyThread
  fun markFinished(event: Megaphones.Event) {
    markFinished(event, null)
  }

  @AnyThread
  fun markFinished(event: Megaphones.Event, onComplete: Runnable?) {
    executor.execute {
      val record = databaseCache[event]
      if (record != null && record.finished) {
        return@execute
      }

      database.markFinished(event)
      resetDatabaseCache()
      onComplete?.run()
    }
  }

  @WorkerThread
  private fun init() {
    val records: MutableList<MegaphoneRecord> = database.getAllAndDeleteMissing()
    val events = records.map { it.event }.toSet()
    val missing = Megaphones.Event.entries - events

    database.insert(missing)
    resetDatabaseCache()
  }

  @WorkerThread
  private fun getRecord(event: Megaphones.Event): MegaphoneRecord {
    return databaseCache.get(event)!!
  }

  @WorkerThread
  private fun resetDatabaseCache() {
    databaseCache.clear()
    databaseCache += database.getAllAndDeleteMissing().associateBy { it.event }
  }

  fun interface Callback<E> {
    fun onResult(result: E?)
  }
}
