package org.thoughtcrime.securesms.emoji

import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.PersistentAlarmManagerListener
import java.util.concurrent.TimeUnit

private val INTERVAL_WITHOUT_REMOTE_DOWNLOAD = TimeUnit.DAYS.toMillis(1)
private val INTERVAL_WITH_REMOTE_DOWNLOAD = TimeUnit.DAYS.toMillis(7)

class EmojiDownloadListener : PersistentAlarmManagerListener() {

  override fun getNextScheduledExecutionTime(context: Context): Long = SignalStore.emojiValues().nextScheduledCheck

  override fun onAlarm(context: Context, scheduledTime: Long): Long {
    ApplicationDependencies.getJobManager().add(DownloadLatestEmojiDataJob(false))

    val nextTime: Long = System.currentTimeMillis() + if (EmojiFiles.Version.exists(context)) INTERVAL_WITH_REMOTE_DOWNLOAD else INTERVAL_WITHOUT_REMOTE_DOWNLOAD

    SignalStore.emojiValues().nextScheduledCheck = nextTime

    return nextTime
  }

  companion object {
    @JvmStatic
    fun schedule(context: Context) {
      EmojiDownloadListener().onReceive(context, Intent())
    }
  }
}
