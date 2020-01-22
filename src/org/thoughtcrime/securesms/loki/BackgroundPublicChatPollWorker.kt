package org.thoughtcrime.securesms.loki

import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.service.PersistentAlarmManagerListener
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.concurrent.TimeUnit

class BackgroundPublicChatPollWorker : PersistentAlarmManagerListener() {

    companion object {
        private val pollInterval = TimeUnit.MINUTES.toMillis(4)

        @JvmStatic
        fun schedule(context: Context) {
            BackgroundPublicChatPollWorker().onReceive(context, Intent())
        }
    }

    override fun getNextScheduledExecutionTime(context: Context): Long {
        return TextSecurePreferences.getPublicChatBackgroundPollTime(context)
    }

    override fun onAlarm(context: Context, scheduledTime: Long): Long {
        if (scheduledTime != 0L) {
            val publicChats = DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().map { it.value }
            for (publicChat in publicChats) {
                val poller = LokiPublicChatPoller(context, publicChat)
                poller.stop()
                poller.pollForNewMessages()
            }
        }
        val nextTime = System.currentTimeMillis() + pollInterval
        TextSecurePreferences.setPublicChatBackgroundPollTime(context, nextTime)
        return nextTime
    }
}
