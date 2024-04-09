package org.thoughtcrime.securesms.service

import android.content.Context
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.AnalyzeDatabaseJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.toMillis
import java.time.LocalDateTime

/**
 * Schedules database analysis to happen everyday at 3am.
 */
class AnalyzeDatabaseAlarmListener : PersistentAlarmManagerListener() {
  companion object {
    @JvmStatic
    fun schedule(context: Context?) {
      AnalyzeDatabaseAlarmListener().onReceive(context, getScheduleIntent())
    }
  }

  override fun shouldScheduleExact(): Boolean {
    return true
  }

  override fun getNextScheduledExecutionTime(context: Context): Long {
    var nextTime = SignalStore.misc().nextDatabaseAnalysisTime

    if (nextTime == 0L) {
      nextTime = getNextTime()
      SignalStore.misc().nextDatabaseAnalysisTime = nextTime
    }

    return nextTime
  }

  override fun onAlarm(context: Context, scheduledTime: Long): Long {
    ApplicationDependencies.getJobManager().add(AnalyzeDatabaseJob())

    val nextTime = getNextTime()
    SignalStore.misc().nextDatabaseAnalysisTime = nextTime

    return nextTime
  }

  private fun getNextTime(): Long {
    return LocalDateTime
      .now()
      .plusDays(1)
      .withHour(3)
      .withMinute(0)
      .withSecond(0)
      .toMillis()
  }
}
