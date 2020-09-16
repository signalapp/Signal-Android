package org.thoughtcrime.securesms.loki.api

import android.content.Context
import android.content.Intent
import nl.komponents.kovenant.functional.map
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob
import org.thoughtcrime.securesms.service.PersistentAlarmManagerListener
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope
import org.whispersystems.signalservice.loki.api.SnodeAPI
import java.util.concurrent.TimeUnit

class BackgroundPollListener : PersistentAlarmManagerListener() {

    companion object {
        private val pollInterval = TimeUnit.MINUTES.toMillis(15)

        @JvmStatic
        fun schedule(context: Context) {
            BackgroundPollListener().onReceive(context, Intent())
        }
    }

    override fun getNextScheduledExecutionTime(context: Context): Long {
        return TextSecurePreferences.getBackgroundPollTime(context)
    }

    override fun onAlarm(context: Context, scheduledTime: Long): Long {
        ApplicationContext.getInstance(context).jobManager.add(BackgroundPollJob(context))
        val nextTime = System.currentTimeMillis() + pollInterval
        TextSecurePreferences.setBackgroundPollTime(context, nextTime)
        return nextTime
    }
}
