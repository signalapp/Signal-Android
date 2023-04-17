package org.thoughtcrime.securesms.messages

import android.annotation.SuppressLint
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import kotlinx.collections.immutable.toImmutableSet
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.signal.core.util.withinTransaction
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobmanager.JobTracker.JobListener
import org.thoughtcrime.securesms.jobmanager.impl.BackoffUtil
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.ForegroundServiceUtil.startWhenCapable
import org.thoughtcrime.securesms.jobs.PushDecryptMessageJob
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob
import org.thoughtcrime.securesms.jobs.PushProcessMessageJobV2
import org.thoughtcrime.securesms.jobs.UnableToStartException
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.MessageDecryptor.FollowUpOperation
import org.thoughtcrime.securesms.messages.protocol.BufferedProtocolStore
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.AppForegroundObserver
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState
import org.whispersystems.signalservice.api.websocket.WebSocketUnavailableException
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The application-level manager of our websocket connection.
 *
 *
 * This class is responsible for opening/closing the websocket based on the app's state and observing new inbound messages received on the websocket.
 */
class IncomingMessageObserver(private val context: Application) {

  companion object {
    private val TAG = Log.tag(IncomingMessageObserver::class.java)
    private val WEBSOCKET_READ_TIMEOUT = TimeUnit.MINUTES.toMillis(1)
    private val KEEP_ALIVE_TOKEN_MAX_AGE = TimeUnit.MINUTES.toMillis(5)
    private val MAX_BACKGROUND_TIME = TimeUnit.MINUTES.toMillis(2)
    private val INSTANCE_COUNT = AtomicInteger(0)

    const val FOREGROUND_ID = 313399
  }

  private val decryptionDrainedListeners: MutableList<Runnable> = CopyOnWriteArrayList()
  private val keepAliveTokens: MutableMap<String, Long> = mutableMapOf()
  private val connectionReceiver: BroadcastReceiver

  private val lock: ReentrantLock = ReentrantLock()
  private val condition: Condition = lock.newCondition()

  private var appVisible = false
  private var lastInteractionTime: Long = System.currentTimeMillis()

  @Volatile
  private var terminated = false

  @Volatile
  var decryptionDrained = false
    private set

  init {
    if (INSTANCE_COUNT.incrementAndGet() != 1) {
      throw AssertionError("Multiple observers!")
    }

    MessageRetrievalThread().start()

    if (!SignalStore.account().fcmEnabled || SignalStore.internalValues().isWebsocketModeForced) {
      try {
        startWhenCapable(context, Intent(context, ForegroundService::class.java))
      } catch (e: UnableToStartException) {
        Log.w(TAG, "Unable to start foreground service for websocket!", e)
      }
    }

    ApplicationDependencies.getAppForegroundObserver().addListener(object : AppForegroundObserver.Listener {
      override fun onForeground() {
        onAppForegrounded()
      }

      override fun onBackground() {
        onAppBackgrounded()
      }
    })

    connectionReceiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        lock.withLock {
          if (!NetworkConstraint.isMet(context)) {
            Log.w(TAG, "Lost network connection. Shutting down our websocket connections and resetting the drained state.")
            decryptionDrained = false
            disconnect()
          }
          condition.signalAll()
        }
      }
    }

    context.registerReceiver(connectionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
  }

  fun notifyRegistrationChanged() {
    lock.withLock {
      condition.signalAll()
    }
  }

  fun addDecryptionDrainedListener(listener: Runnable) {
    decryptionDrainedListeners.add(listener)
    if (decryptionDrained) {
      listener.run()
    }
  }

  fun removeDecryptionDrainedListener(listener: Runnable) {
    decryptionDrainedListeners.remove(listener)
  }

  fun notifyDecryptionsDrained() {
    if (ApplicationDependencies.getJobManager().isQueueEmpty(PushDecryptMessageJob.QUEUE)) {
      Log.i(TAG, "Queue was empty when notified. Signaling change.")
      lock.withLock {
        condition.signalAll()
      }
    } else {
      Log.i(TAG, "Queue still had items when notified. Registering listener to signal change.")
      ApplicationDependencies.getJobManager().addListener(
        { it.parameters.queue == PushDecryptMessageJob.QUEUE },
        DecryptionDrainedQueueListener()
      )
    }
  }

  private fun onAppForegrounded() {
    lock.withLock {
      appVisible = true
      BackgroundService.start(context)
      condition.signalAll()
    }
  }

  private fun onAppBackgrounded() {
    lock.withLock {
      appVisible = false
      lastInteractionTime = System.currentTimeMillis()
      condition.signalAll()
    }
  }

  private fun isConnectionNecessary(): Boolean {
    val timeIdle: Long
    val keepAliveEntries: Set<Map.Entry<String, Long>>
    val appVisibleSnapshot: Boolean

    lock.withLock {
      appVisibleSnapshot = appVisible
      timeIdle = if (appVisibleSnapshot) 0 else System.currentTimeMillis() - lastInteractionTime

      val keepAliveCutoffTime = System.currentTimeMillis() - KEEP_ALIVE_TOKEN_MAX_AGE
      val removedKeepAliveToken = keepAliveTokens.entries.removeIf { (_, createTime) -> createTime < keepAliveCutoffTime }
      if (removedKeepAliveToken) {
        Log.d(TAG, "Removed old keep web socket open requests.")
      }

      keepAliveEntries = keepAliveTokens.entries.toImmutableSet()
    }

    val registered = SignalStore.account().isRegistered
    val fcmEnabled = SignalStore.account().fcmEnabled
    val hasNetwork = NetworkConstraint.isMet(context)
    val hasProxy = SignalStore.proxy().isProxyEnabled
    val forceWebsocket = SignalStore.internalValues().isWebsocketModeForced
    val decryptQueueEmpty = ApplicationDependencies.getJobManager().isQueueEmpty(PushDecryptMessageJob.QUEUE)

    val lastInteractionString = if (appVisibleSnapshot) "N/A" else timeIdle.toString() + " ms (" + (if (timeIdle < MAX_BACKGROUND_TIME) "within limit" else "over limit") + ")"
    val conclusion = registered &&
      (appVisibleSnapshot || timeIdle < MAX_BACKGROUND_TIME || !fcmEnabled || keepAliveEntries.isNotEmpty()) &&
      hasNetwork &&
      decryptQueueEmpty

    val needsConnectionString = if (conclusion) "Needs Connection" else "Does Not Need Connection"

    Log.d(TAG, "[$needsConnectionString] Network: $hasNetwork, Foreground: $appVisibleSnapshot, Time Since Last Interaction: $lastInteractionString, FCM: $fcmEnabled, Stay open requests: $keepAliveEntries, Registered: $registered, Proxy: $hasProxy, Force websocket: $forceWebsocket, Decrypt Queue Empty: $decryptQueueEmpty")
    return conclusion
  }

  private fun waitForConnectionNecessary() {
    lock.withLock {
      try {
        while (!isConnectionNecessary()) {
          condition.await()
        }
      } catch (e: InterruptedException) {
        throw AssertionError(e)
      }
    }
  }

  fun terminateAsync() {
    Log.w(TAG, "Termination Enqueued! ${this.hashCode()}", Throwable())
    INSTANCE_COUNT.decrementAndGet()
    context.unregisterReceiver(connectionReceiver)

    SignalExecutors.BOUNDED.execute {
      Log.w(TAG, "Beginning termination. ${this.hashCode()}")
      terminated = true
      disconnect()
    }
  }

  private fun disconnect() {
    ApplicationDependencies.getSignalWebSocket().disconnect()
  }

  fun registerKeepAliveToken(key: String) {
    lock.withLock {
      keepAliveTokens[key] = System.currentTimeMillis()
      lastInteractionTime = System.currentTimeMillis()
      condition.signalAll()
    }
  }

  fun removeKeepAliveToken(key: String) {
    lock.withLock {
      keepAliveTokens.remove(key)
      lastInteractionTime = System.currentTimeMillis()
      condition.signalAll()
    }
  }

  @VisibleForTesting
  fun processEnvelope(bufferedProtocolStore: BufferedProtocolStore, envelope: SignalServiceProtos.Envelope, serverDeliveredTimestamp: Long): List<FollowUpOperation>? {
    return when (envelope.type.number) {
      SignalServiceProtos.Envelope.Type.RECEIPT_VALUE -> {
        processReceipt(envelope)
        null
      }

      SignalServiceProtos.Envelope.Type.PREKEY_BUNDLE_VALUE,
      SignalServiceProtos.Envelope.Type.CIPHERTEXT_VALUE,
      SignalServiceProtos.Envelope.Type.UNIDENTIFIED_SENDER_VALUE,
      SignalServiceProtos.Envelope.Type.PLAINTEXT_CONTENT_VALUE -> {
        processMessage(bufferedProtocolStore, envelope, serverDeliveredTimestamp)
      }

      else -> {
        Log.w(TAG, "Received envelope of unknown type: " + envelope.type)
        null
      }
    }
  }

  private fun processMessage(bufferedProtocolStore: BufferedProtocolStore, envelope: SignalServiceProtos.Envelope, serverDeliveredTimestamp: Long): List<FollowUpOperation> {
    val result = MessageDecryptor.decrypt(context, bufferedProtocolStore, envelope, serverDeliveredTimestamp)

    val extraJob: Job? = when (result) {
      is MessageDecryptor.Result.Success -> {
        PushProcessMessageJobV2(result.envelope, result.content, result.metadata, result.serverDeliveredTimestamp)
      }

      is MessageDecryptor.Result.Error -> {
        PushProcessMessageJob(
          result.toMessageState(),
          null,
          result.errorMetadata.toExceptionMetadata(),
          -1,
          result.envelope.timestamp
        )
      }

      is MessageDecryptor.Result.Ignore -> {
        // No action needed
        null
      }

      else -> {
        throw AssertionError("Unexpected result! ${result.javaClass.simpleName}")
      }
    }

    return result.followUpOperations + FollowUpOperation { extraJob }
  }

  private fun processReceipt(envelope: SignalServiceProtos.Envelope) {
    if (!UuidUtil.isUuid(envelope.sourceUuid)) {
      Log.w(TAG, "Invalid envelope source UUID!")
      return
    }

    val senderId = RecipientId.from(ServiceId.parseOrThrow(envelope.sourceUuid))

    Log.i(TAG, "Received server receipt. Sender: $senderId, Device: ${envelope.sourceDevice}, Timestamp: ${envelope.timestamp}")
    SignalDatabase.messages.incrementDeliveryReceiptCount(MessageTable.SyncMessageId(senderId, envelope.timestamp), System.currentTimeMillis())
    SignalDatabase.messageLog.deleteEntryForRecipient(envelope.timestamp, senderId, envelope.sourceDevice)
  }

  private fun MessageDecryptor.Result.toMessageState(): MessageContentProcessor.MessageState {
    return when (this) {
      is MessageDecryptor.Result.DecryptionError -> MessageContentProcessor.MessageState.DECRYPTION_ERROR
      is MessageDecryptor.Result.Ignore -> MessageContentProcessor.MessageState.NOOP
      is MessageDecryptor.Result.InvalidVersion -> MessageContentProcessor.MessageState.INVALID_VERSION
      is MessageDecryptor.Result.LegacyMessage -> MessageContentProcessor.MessageState.LEGACY_MESSAGE
      is MessageDecryptor.Result.Success -> MessageContentProcessor.MessageState.DECRYPTED_OK
      is MessageDecryptor.Result.UnsupportedDataMessage -> MessageContentProcessor.MessageState.UNSUPPORTED_DATA_MESSAGE
    }
  }

  private fun MessageDecryptor.ErrorMetadata.toExceptionMetadata(): MessageContentProcessor.ExceptionMetadata {
    return MessageContentProcessor.ExceptionMetadata(
      this.sender,
      this.senderDevice,
      this.groupId
    )
  }

  private inner class MessageRetrievalThread : Thread("MessageRetrievalService"), Thread.UncaughtExceptionHandler {

    init {
      Log.i(TAG, "Initializing! (${this.hashCode()})")
      uncaughtExceptionHandler = this
    }

    override fun run() {
      var attempts = 0

      while (!terminated) {
        Log.i(TAG, "Waiting for websocket state change....")
        if (attempts > 1) {
          val backoff = BackoffUtil.exponentialBackoff(attempts, TimeUnit.SECONDS.toMillis(30))
          Log.w(TAG, "Too many failed connection attempts,  attempts: $attempts backing off: $backoff")
          ThreadUtil.sleep(backoff)
        }

        waitForConnectionNecessary()
        Log.i(TAG, "Making websocket connection....")

        val signalWebSocket = ApplicationDependencies.getSignalWebSocket()
        val webSocketDisposable = signalWebSocket.webSocketState.subscribe { state: WebSocketConnectionState ->
          Log.d(TAG, "WebSocket State: $state")

          // Any state change at all means that we are not drained
          decryptionDrained = false
        }

        signalWebSocket.connect()
        try {
          while (isConnectionNecessary()) {
            try {
              Log.d(TAG, "Reading message...")

              val hasMore = signalWebSocket.readMessageBatch(WEBSOCKET_READ_TIMEOUT, 30) { batch ->
                Log.i(TAG, "Retrieved ${batch.size} envelopes!")
                val bufferedStore = BufferedProtocolStore.create()

                val startTime = System.currentTimeMillis()
                ReentrantSessionLock.INSTANCE.acquire().use {
                  SignalDatabase.rawDatabase.withinTransaction {
                    val followUpOperations: List<FollowUpOperation> = batch
                      .mapNotNull { processEnvelope(bufferedStore, it.envelope, it.serverDeliveredTimestamp) }
                      .flatten()

                    bufferedStore.flushToDisk()

                    val jobs = followUpOperations.mapNotNull { it.run() }
                    ApplicationDependencies.getJobManager().addAll(jobs)
                  }
                }

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "Decrypted ${batch.size} envelopes in $duration ms (~${duration / batch.size} ms per message)")

                true
              }

              attempts = 0

              if (!hasMore && !decryptionDrained) {
                Log.i(TAG, "Decryptions newly-drained.")
                decryptionDrained = true

                for (listener in decryptionDrainedListeners.toList()) {
                  listener.run()
                }
              } else if (!hasMore) {
                Log.w(TAG, "Got tombstone, but we thought the network was already drained!")
              }
            } catch (e: WebSocketUnavailableException) {
              Log.i(TAG, "Pipe unexpectedly unavailable, connecting")
              signalWebSocket.connect()
            } catch (e: TimeoutException) {
              Log.w(TAG, "Application level read timeout...")
              attempts = 0
            }
          }

          if (!appVisible) {
            BackgroundService.stop(context)
          }
        } catch (e: Throwable) {
          attempts++
          Log.w(TAG, e)
        } finally {
          Log.w(TAG, "Shutting down pipe...")
          disconnect()
          webSocketDisposable.dispose()
        }
        Log.i(TAG, "Looping...")
      }
      Log.w(TAG, "Terminated! (${this.hashCode()})")
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
      Log.w(TAG, "Uncaught exception in message thread!", e)
    }
  }

  private inner class DecryptionDrainedQueueListener : JobListener {
    @SuppressLint("WrongThread")
    override fun onStateChanged(job: Job, jobState: JobTracker.JobState) {
      if (jobState.isComplete) {
        if (ApplicationDependencies.getJobManager().isQueueEmpty(PushDecryptMessageJob.QUEUE)) {
          Log.i(TAG, "Queue is now empty. Signaling change.")
          lock.withLock {
            condition.signalAll()
          }
          ApplicationDependencies.getJobManager().removeListener(this)
        } else {
          Log.i(TAG, "Item finished in queue, but it's still not empty. Waiting to signal change.")
        }
      }
    }
  }

  class ForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
      return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      super.onStartCommand(intent, flags, startId)

      val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.getInstance().BACKGROUND)
        .setContentTitle(applicationContext.getString(R.string.MessageRetrievalService_signal))
        .setContentText(applicationContext.getString(R.string.MessageRetrievalService_background_connection_enabled))
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setWhen(0)
        .setSmallIcon(R.drawable.ic_signal_background_connection)
        .build()

      startForeground(FOREGROUND_ID, notification)

      return START_STICKY
    }
  }

  /**
   * A service that exists just to encourage the system to keep our process alive a little longer.
   */
  class BackgroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      Log.d(TAG, "Background service started.")
      return START_STICKY
    }

    override fun onDestroy() {
      Log.d(TAG, "Background service destroyed.")
    }

    companion object {
      fun start(context: Context) {
        try {
          context.startService(Intent(context, BackgroundService::class.java))
        } catch (e: Exception) {
          Log.w(TAG, "Failed to start background service.", e)
        }
      }

      fun stop(context: Context) {
        context.stopService(Intent(context, BackgroundService::class.java))
      }
    }
  }
}
