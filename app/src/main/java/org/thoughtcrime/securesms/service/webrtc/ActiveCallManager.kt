/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.UnableToStartException
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.SafeForegroundService
import org.thoughtcrime.securesms.util.TelephonyUtil
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.Companion.create
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Entry point for [SignalCallManager] and friends to interact with the Android system.
 *
 * This tries to limit the use of a foreground service until a call has been fully established
 * and the user has likely foregrounded us by accepting a call.
 */
class ActiveCallManager(
  private val application: Context
) : SignalAudioManager.EventListener {

  companion object {
    private val TAG = Log.tag(ActiveCallManager::class.java)

    private var activeCallManager: ActiveCallManager? = null
    private val activeCallManagerLock = ReentrantLock()

    @JvmStatic
    fun clearNotifications(context: Context) {
      NotificationManagerCompat.from(context).apply {
        cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION)
        cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_RINGING)
      }
    }

    @JvmStatic
    fun update(context: Context, type: Int, recipientId: RecipientId, isVideoCall: Boolean) {
      activeCallManagerLock.withLock {
        if (activeCallManager == null) {
          activeCallManager = ActiveCallManager(context)
        }
        activeCallManager!!.update(type, recipientId, isVideoCall)
      }
    }

    @JvmStatic
    fun denyCall() {
      AppDependencies.signalCallManager.denyCall()
    }

    @JvmStatic
    fun hangup() {
      AppDependencies.signalCallManager.localHangup()
    }

    @JvmStatic
    fun stop() {
      activeCallManagerLock.withLock {
        activeCallManager?.shutdown()
        activeCallManager = null
      }
    }

    @JvmStatic
    fun denyCallIntent(context: Context): PendingIntent {
      val intent = Intent(context, ActiveCallServiceReceiver::class.java)
      intent.setAction(ActiveCallServiceReceiver.ACTION_DENY)
      return PendingIntent.getBroadcast(context, 0, intent, PendingIntentFlags.mutable())
    }

    @JvmStatic
    fun hangupIntent(context: Context): PendingIntent {
      val intent = Intent(context, ActiveCallServiceReceiver::class.java)
      intent.setAction(ActiveCallServiceReceiver.ACTION_HANGUP)
      return PendingIntent.getBroadcast(context, 0, intent, PendingIntentFlags.mutable())
    }

    @JvmStatic
    fun sendAudioManagerCommand(context: Context, command: AudioManagerCommand) {
      activeCallManagerLock.withLock {
        if (activeCallManager == null) {
          activeCallManager = ActiveCallManager(context)
        }
        activeCallManager!!.sendAudioCommand(command)
      }
    }

    @JvmStatic
    fun changePowerButtonReceiver(context: Context, register: Boolean) {
      activeCallManagerLock.withLock {
        if (activeCallManager == null) {
          activeCallManager = ActiveCallManager(context)
        }
        activeCallManager!!.changePowerButton(register)
      }
    }
  }

  private val callManager = AppDependencies.signalCallManager

  private var networkReceiver: NetworkReceiver? = null
  private var powerButtonReceiver: PowerButtonReceiver? = null
  private var uncaughtExceptionHandlerManager: UncaughtExceptionHandlerManager? = null
  private val webSocketKeepAliveTask: WebSocketKeepAliveTask = WebSocketKeepAliveTask()
  private var signalAudioManager: SignalAudioManager? = null
  private var previousNotificationId = -1

  init {
    registerUncaughtExceptionHandler()
    registerNetworkReceiver()

    webSocketKeepAliveTask.start()
  }

  fun shutdown() {
    Log.v(TAG, "shutdown")

    uncaughtExceptionHandlerManager?.unregister()
    uncaughtExceptionHandlerManager = null

    signalAudioManager?.shutdown()
    signalAudioManager = null

    unregisterNetworkReceiver()
    unregisterPowerButtonReceiver()

    webSocketKeepAliveTask.stop()

    if (!ActiveCallForegroundService.stop(application) && previousNotificationId != -1) {
      NotificationManagerCompat.from(application).cancel(previousNotificationId)
    }
  }

  fun update(type: Int, recipientId: RecipientId, isVideoCall: Boolean) {
    Log.i(TAG, "update $type $recipientId $isVideoCall")

    val notificationId = CallNotificationBuilder.getNotificationId(type)

    if (previousNotificationId != notificationId && previousNotificationId != -1) {
      NotificationManagerCompat.from(application).cancel(previousNotificationId)
    }

    previousNotificationId = notificationId

    if (type != CallNotificationBuilder.TYPE_ESTABLISHED) {
      val notification = CallNotificationBuilder.getCallInProgressNotification(application, type, Recipient.resolved(recipientId), isVideoCall, false)
      NotificationManagerCompat.from(application).notify(notificationId, notification)
    } else {
      ActiveCallForegroundService.start(application, recipientId, isVideoCall)
    }
  }

  fun sendAudioCommand(audioCommand: AudioManagerCommand) {
    if (signalAudioManager == null) {
      signalAudioManager = create(application, this)
    }

    Log.i(TAG, "Sending audio command [" + audioCommand.javaClass.simpleName + "] to " + signalAudioManager?.javaClass?.simpleName)
    signalAudioManager!!.handleCommand(audioCommand)
  }

  fun changePowerButton(enabled: Boolean) {
    if (enabled) {
      registerPowerButtonReceiver()
    } else {
      unregisterPowerButtonReceiver()
    }
  }

  private fun registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = UncaughtExceptionHandlerManager()
    uncaughtExceptionHandlerManager!!.registerHandler(ProximityLockRelease(callManager.lockManager))
  }

  private fun registerNetworkReceiver() {
    if (networkReceiver == null) {
      networkReceiver = NetworkReceiver()
      application.registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }
  }

  private fun unregisterNetworkReceiver() {
    if (networkReceiver != null) {
      application.unregisterReceiver(networkReceiver)
      networkReceiver = null
    }
  }

  private fun registerPowerButtonReceiver() {
    if (!AndroidTelecomUtil.telecomSupported && powerButtonReceiver == null) {
      powerButtonReceiver = PowerButtonReceiver()
      application.registerReceiver(powerButtonReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }
  }

  private fun unregisterPowerButtonReceiver() {
    if (powerButtonReceiver != null) {
      application.unregisterReceiver(powerButtonReceiver)
      powerButtonReceiver = null
    }
  }

  override fun onAudioDeviceChanged(activeDevice: SignalAudioManager.AudioDevice, devices: Set<SignalAudioManager.AudioDevice>) {
    callManager.onAudioDeviceChanged(activeDevice, devices)
  }

  override fun onBluetoothPermissionDenied() {
    callManager.onBluetoothPermissionDenied()
  }

  /** Foreground service started only after a call is established */
  class ActiveCallForegroundService : SafeForegroundService() {
    companion object {
      private const val EXTRA_RECIPIENT_ID = "RECIPIENT_ID"
      private const val EXTRA_IS_VIDEO_CALL = "IS_VIDEO_CALL"

      fun start(context: Context, recipientId: RecipientId, isVideoCall: Boolean) {
        val extras = bundleOf(
          EXTRA_RECIPIENT_ID to recipientId,
          EXTRA_IS_VIDEO_CALL to isVideoCall
        )

        if (!SafeForegroundService.start(context, ActiveCallForegroundService::class.java, extras)) {
          throw UnableToStartException(Exception())
        }
      }

      fun stop(context: Context): Boolean {
        return SafeForegroundService.stop(context, ActiveCallForegroundService::class.java)
      }
    }

    override val tag: String
      get() = TAG

    override val notificationId: Int
      get() = CallNotificationBuilder.WEBRTC_NOTIFICATION

    @get:RequiresApi(30)
    override val serviceType: Int
      get() = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

    private var hangUpRtcOnDeviceCallAnswered: PhoneStateListener? = null
    private var notification: Notification? = null
    private var notificationDisposable: Disposable = Disposable.disposed()

    override fun onCreate() {
      super.onCreate()
      hangUpRtcOnDeviceCallAnswered = HangUpRtcOnPstnCallAnsweredListener()

      if (!AndroidTelecomUtil.telecomSupported) {
        try {
          TelephonyUtil.getManager(application).listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
          Log.w(TAG, "Failed to listen to PSTN call answers!", e)
        }
      }
    }

    override fun onDestroy() {
      notificationDisposable.dispose()

      super.onDestroy()

      if (!AndroidTelecomUtil.telecomSupported) {
        TelephonyUtil.getManager(application).listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_NONE)
      }
    }

    override fun getForegroundNotification(intent: Intent): Notification {
      if (SafeForegroundService.isStopping(intent)) {
        Log.v(TAG, "Service is stopping, using generic stopping notification")
        return CallNotificationBuilder.getStoppingNotification(this)
      }

      if (notification != null) {
        return notification!!
      } else if (!intent.hasExtra(EXTRA_RECIPIENT_ID)) {
        return CallNotificationBuilder.getStoppingNotification(this)
      }

      val recipient: Recipient = Recipient.resolved(intent.getParcelableExtra(EXTRA_RECIPIENT_ID)!!)
      val isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false)
      val requiresAsyncNotificationLoad = Build.VERSION.SDK_INT <= 29

      notification = createNotification(recipient, isVideoCall, skipAvatarLoad = requiresAsyncNotificationLoad)

      if (requiresAsyncNotificationLoad) {
        notificationDisposable = Single.fromCallable { createNotification(recipient, isVideoCall, skipAvatarLoad = false) }
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy {
            notification = it
            if (NotificationManagerCompat.from(this).activeNotifications.any { n -> n.id == notificationId }) {
              NotificationManagerCompat.from(application).notify(notificationId, notification!!)
            }
          }
      }

      return notification!!
    }

    private fun createNotification(recipient: Recipient, isVideoCall: Boolean, skipAvatarLoad: Boolean): Notification {
      return CallNotificationBuilder.getCallInProgressNotification(
        this,
        CallNotificationBuilder.TYPE_ESTABLISHED,
        recipient,
        isVideoCall,
        skipAvatarLoad
      )
    }

    @Suppress("deprecation")
    private class HangUpRtcOnPstnCallAnsweredListener : PhoneStateListener() {
      override fun onCallStateChanged(state: Int, phoneNumber: String) {
        super.onCallStateChanged(state, phoneNumber)
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
          hangup()
          Log.i(TAG, "Device phone call ended Signal call.")
        }
      }

      private fun hangup() {
        AppDependencies.signalCallManager.localHangup()
      }
    }
  }

  class ActiveCallServiceReceiver : BroadcastReceiver() {

    companion object {
      const val ACTION_DENY = "org.thoughtcrime.securesms.service.webrtc.ActiveCallAction.DENY"
      const val ACTION_HANGUP = "org.thoughtcrime.securesms.service.webrtc.ActiveCallAction.HANGUP"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
      Log.d(TAG, "action: ${intent?.action}")
      when (intent?.action) {
        ACTION_DENY -> AppDependencies.signalCallManager.denyCall()
        ACTION_HANGUP -> AppDependencies.signalCallManager.localHangup()
      }
    }
  }

  /**
   * Periodically request the web socket stay open if we are doing anything call related.
   */
  private class WebSocketKeepAliveTask : Runnable {

    companion object {
      private val REQUEST_WEBSOCKET_STAY_OPEN_DELAY: Duration = 1.minutes
      private val WEBSOCKET_KEEP_ALIVE_TOKEN: String = WebSocketKeepAliveTask::class.java.simpleName
    }

    private var keepRunning = false

    @MainThread
    fun start() {
      if (!keepRunning) {
        keepRunning = true
        run()
      }
    }

    @MainThread
    fun stop() {
      keepRunning = false
      ThreadUtil.cancelRunnableOnMain(this)
      AppDependencies.incomingMessageObserver.removeKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN)
    }

    @MainThread
    override fun run() {
      if (keepRunning) {
        AppDependencies.incomingMessageObserver.registerKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN)
        ThreadUtil.runOnMainDelayed(this, REQUEST_WEBSOCKET_STAY_OPEN_DELAY.inWholeMilliseconds)
      }
    }
  }

  private class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val activeNetworkInfo = connectivityManager.activeNetworkInfo

      AppDependencies.signalCallManager.apply {
        networkChange(activeNetworkInfo != null && activeNetworkInfo.isConnected)
        dataModeUpdate()
      }
    }
  }

  private class PowerButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (Intent.ACTION_SCREEN_OFF == intent.action) {
        AppDependencies.signalCallManager.screenOff()
      }
    }
  }

  private class ProximityLockRelease(private val lockManager: LockManager) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
      Log.i(TAG, "Uncaught exception - releasing proximity lock", throwable)
      lockManager.updatePhoneState(LockManager.PhoneState.IDLE)
    }
  }
}
