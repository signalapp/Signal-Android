package org.thoughtcrime.securesms.loki

import android.content.Context
import android.os.Handler
import android.util.Log
import com.prof.rssparser.Parser
import kotlinx.coroutines.*
import org.thoughtcrime.securesms.jobs.PushDecryptJob
import org.whispersystems.libsignal.util.guava.Optional
import org.whispersystems.signalservice.api.messages.SignalServiceContent
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage
import org.whispersystems.signalservice.api.messages.SignalServiceGroup
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import org.whispersystems.signalservice.loki.api.LokiRSSFeed
import java.text.SimpleDateFormat

class LokiRSSFeedPoller(private val context: Context, private val feed: LokiRSSFeed) {
    private val handler = Handler()
    private val job = Job()
    private var hasStarted = false

    private val task = object : Runnable {

        override fun run() {
            poll()
            handler.postDelayed(this, interval)
        }
    }

    companion object {
        private val interval: Long = 8 * 60 * 1000
    }

    fun startIfNeeded() {
        if (hasStarted) return
        task.run()
        hasStarted = true
    }

    fun stop() {
        handler.removeCallbacks(task)
        job.cancel()
        hasStarted = false
    }

    private fun poll() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val url = feed.url
                val parser = Parser()
                val items = parser.getArticles(url)
                items.reversed().forEach { item ->
                    val title = item.title ?: return@forEach
                    val description = item.description ?: return@forEach
                    val dateAsString = item.pubDate ?: return@forEach
                    val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z") // e.g. Tue, 27 Aug 2019 03:52:05 +0000
                    val date = formatter.parse(dateAsString)
                    val timestamp = date.time
                    val body = "$title<br>$description"
                    val id = feed.id.toByteArray()
                    val x1 = SignalServiceGroup(SignalServiceGroup.Type.UPDATE, id, null, null, null)
                    val x2 = SignalServiceDataMessage(timestamp, x1, null, body)
                    val x3 = SignalServiceContent(x2, "Loki", SignalServiceAddress.DEFAULT_DEVICE_ID, timestamp, false)
                    PushDecryptJob(context).handleTextMessage(x3, x2, Optional.absent())
                }
            } catch (exception: Exception) {
                Log.d("Loki", "Couldn't update RSS feed with ID: $feed.id.")
            }
        }
    }
}