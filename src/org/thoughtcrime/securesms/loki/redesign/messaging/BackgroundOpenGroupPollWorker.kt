package org.thoughtcrime.securesms.loki.redesign.messaging

import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.service.PersistentAlarmManagerListener
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.util.concurrent.TimeUnit

class BackgroundOpenGroupPollWorker : PersistentAlarmManagerListener() {

    companion object {
        private val pollInterval = TimeUnit.MINUTES.toMillis(4)

        @JvmStatic
        fun schedule(context: Context) {
            BackgroundOpenGroupPollWorker().onReceive(context, Intent())
        }
    }

    override fun getNextScheduledExecutionTime(context: Context): Long {
        return TextSecurePreferences.getOpenGroupBackgroundPollTime(context)
    }

    override fun onAlarm(context: Context, scheduledTime: Long): Long {
        if (scheduledTime != 0L) {
            val openGroups = DatabaseFactory.getLokiThreadDatabase(context).getAllPublicChats().map { it.value }
            for (openGroup in openGroups) {
                val poller = LokiPublicChatPoller(context, openGroup)
                poller.stop()
                poller.pollForNewMessages()
            }
        }
        val nextTime = System.currentTimeMillis() + pollInterval
        TextSecurePreferences.setOpenGroupBackgroundPollTime(context, nextTime)
        return nextTime
    }
}
