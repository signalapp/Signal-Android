package org.thoughtcrime.securesms.logging

import android.app.Application
import android.os.Looper
import org.signal.core.util.logging.Log
import org.signal.core.util.logging.Scrubber
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.database.model.LogEntry
import org.thoughtcrime.securesms.logging.PersistentLogger.LogRequest
import org.thoughtcrime.securesms.logging.PersistentLogger.WriteThread
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A logger that will persist log entries in [LogDatabase].
 *
 * We log everywhere, and we never want it to slow down the app, so performance is critical here.
 * This class takes special care to do as little as possible on the main thread, instead letting the background thread do the work.
 *
 * The process looks something like:
 * - Main thread creates a [LogRequest] object and puts it in a queue
 * - The [WriteThread] constantly pulls from that queue, formats the logs, and writes them to the database.
 */
class PersistentLogger(
  application: Application
) : Log.Logger() {

  companion object {
    private const val LOG_V = "V"
    private const val LOG_D = "D"
    private const val LOG_I = "I"
    private const val LOG_W = "W"
    private const val LOG_E = "E"
  }

  private val logEntries = LogRequests()
  private val logDatabase = LogDatabase.getInstance(application)
  private val cachedThreadString: ThreadLocal<String> = ThreadLocal()

  init {
    WriteThread(logEntries, logDatabase).apply {
      priority = Thread.MIN_PRIORITY
    }.start()
  }

  override fun v(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) {
    write(LOG_V, tag, message, t, keepLonger)
  }

  override fun d(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) {
    write(LOG_D, tag, message, t, keepLonger)
  }

  override fun i(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) {
    write(LOG_I, tag, message, t, keepLonger)
  }

  override fun w(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) {
    write(LOG_W, tag, message, t, keepLonger)
  }

  override fun e(tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) {
    write(LOG_E, tag, message, t, keepLonger)
  }

  override fun flush() {
    logEntries.blockForFlushed()
  }

  private fun write(level: String, tag: String?, message: String?, t: Throwable?, keepLonger: Boolean) {
    logEntries.add(LogRequest(level, tag ?: "null", message, System.currentTimeMillis(), getThreadString(), t, keepLonger))
  }

  private fun getThreadString(): String {
    var threadString = cachedThreadString.get()

    if (cachedThreadString.get() == null) {
      threadString = if (Looper.myLooper() == Looper.getMainLooper()) {
        "main "
      } else {
        String.format("%-5s", Thread.currentThread().id)
      }

      cachedThreadString.set(threadString)
    }

    return threadString!!
  }

  private data class LogRequest(
    val level: String,
    val tag: String,
    val message: String?,
    val createTime: Long,
    val threadString: String,
    val throwable: Throwable?,
    val keepLonger: Boolean
  )

  private class WriteThread(
    private val requests: LogRequests,
    private val db: LogDatabase
  ) : Thread("signal-logger") {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS zzz", Locale.US)
    private val buffer = mutableListOf<LogRequest>()

    override fun run() {
      while (true) {
        requests.blockForRequests(buffer)
        db.insert(buffer.flatMap { requestToEntries(it) }, System.currentTimeMillis())
        buffer.clear()
        requests.notifyFlushed()
      }
    }

    fun requestToEntries(request: LogRequest): List<LogEntry> {
      val out = mutableListOf<LogEntry>()

      val createDate = Date(request.createTime)

      out.add(
        LogEntry(
          createdAt = request.createTime,
          keepLonger = request.keepLonger,
          body = formatBody(request.threadString, createDate, request.level, request.tag, request.message)
        )
      )

      if (request.throwable != null) {
        val outputStream = ByteArrayOutputStream()
        request.throwable.printStackTrace(PrintStream(outputStream))

        val trace = String(outputStream.toByteArray())
        val lines = trace.split("\\n".toRegex()).toTypedArray()

        val entries = lines.map { line ->
          LogEntry(
            createdAt = request.createTime,
            keepLonger = request.keepLonger,
            body = formatBody(request.threadString, createDate, request.level, request.tag, line)
          )
        }

        out.addAll(entries)
      }

      return out
    }

    fun formatBody(threadString: String, date: Date, level: String, tag: String, message: String?): String {
      return "[${BuildConfig.VERSION_NAME}] [$threadString] ${dateFormat.format(date)} $level $tag: ${Scrubber.scrub(message ?: "")}"
    }
  }

  private class LogRequests {
    val logs = mutableListOf<LogRequest>()
    val logLock = Object()

    var flushed = false
    val flushedLock = Object()

    fun add(entry: LogRequest) {
      synchronized(logLock) {
        logs.add(entry)
        logLock.notify()
      }
    }

    /**
     * Blocks until requests are available. When they are, the [buffer] will be populated with all pending requests.
     * Note: This method gets hit a *lot*, which is why we're using a buffer instead of spamming out new lists every time.
     */
    fun blockForRequests(buffer: MutableList<LogRequest>) {
      synchronized(logLock) {
        while (logs.isEmpty()) {
          logLock.wait()
        }

        buffer.addAll(logs)
        logs.clear()
        flushed = false
      }
    }

    fun blockForFlushed() {
      synchronized(flushedLock) {
        while (!flushed) {
          flushedLock.wait()
        }
      }
    }

    fun notifyFlushed() {
      synchronized(flushedLock) {
        flushed = true
        flushedLock.notify()
      }
    }
  }
}
