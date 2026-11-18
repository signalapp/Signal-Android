/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.os.bundleOf
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.models.recipients.RecipientId
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.AppForegroundObserver
import org.signal.core.util.DeviceProperties
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.SafeForegroundService
import org.signal.core.util.ThreadUtil
import org.signal.core.util.UnableToStartException
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.v2.CallIntent
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.TelephonyUtil
import org.thoughtcrime.securesms.webrtc.CallNotificationBuilder
import org.thoughtcrime.securesms.webrtc.UncaughtExceptionHandlerManager
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCommand
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager.Companion.create
import org.thoughtcrime.securesms.webrtc.audio.TelecomAudioManager
import org.thoughtcrime.securesms.webrtc.locks.LockManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

    private const val WEBSOCKET_KEEP_ALIVE_TOKEN: String = "ActiveCall"

    private val requiresAsyncNotificationLoad = Build.VERSION.SDK_INT <= 29

    private var activeCallManager: ActiveCallManager? = null
    private val activeCallManagerLock = ReentrantLock()

    @JvmStatic
    fun clearNotifications(context: Context) {
      NotificationManagerCompat.from(context).apply {
        cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION)
        cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_RINGING)
        cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_MICROPHONE_PERMISSION)
      }
    }

    @JvmStatic
    fun update(context: Context, type: Int, recipientId: RecipientId, isVideoCall: Boolean, usesTelecom: Boolean) {
      activeCallManagerLock.withLock {
        if (activeCallManager == null) {
          activeCallManager = ActiveCallManager(context)
        }
        activeCallManager!!.update(type, recipientId, isVideoCall, usesTelecom)
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

    fun promptToOpenCallScreen(context: Context, @StringRes bodyRes: Int, answerCall: Boolean = false) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(CallNotificationBuilder.WEBRTC_NOTIFICATION_MICROPHONE_PERMISSION, CallNotificationBuilder.getMicrophonePermissionNotification(context, bodyRes, answerCall))
      } else {
        Log.w(TAG, "Notification permission is not granted, skipping call screen notification")
      }

      val builder = CallIntent.Builder(context).withAddedIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (answerCall) {
        builder.withAction(CallIntent.Action.ANSWER_AUDIO)
      }
      val intent = builder.build()
      try {
        context.startActivity(intent)
      } catch (e: Exception) {
        Log.w(TAG, "Unable to launch call screen", e)
      }
    }

    @JvmStatic
    fun onCallPermissionsGranted(context: Context) {
      NotificationManagerCompat.from(context).cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_MICROPHONE_PERMISSION)
      ActiveCallForegroundService.refreshServiceType(context, "permissions granted")
    }

    @JvmStatic
    fun onPhoneStatePermissionGranted() {
      activeCallManagerLock.withLock {
        activeCallManager?.retryPstnCallAnsweredListener()
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
  private var signalAudioManager: SignalAudioManager? = null
  private var previousNotificationId = -1
  private var previousNotificationType = -1
  private var previousNotificationDisposable = Disposable.disposed()

  @Suppress("DEPRECATION")
  private var pstnCallAnsweredListener: PhoneStateListener? = null
  private var shouldListenForPstnCallAnswered = false
  private var usesTelecom = false

  private val foregroundListener = object : AppForegroundObserver.Listener {
    override fun onForeground() {
      if (ActiveCallForegroundService.isServiceTypeDegraded) {
        ActiveCallForegroundService.refreshServiceType(application, "app foregrounded")
      }
    }
  }

  init {
    Log.i(TAG, "init(bkgRestricted: ${DeviceProperties.isBackgroundRestricted()})")

    registerUncaughtExceptionHandler()
    registerNetworkReceiver()
    AppForegroundObserver.addListener(foregroundListener)

    AppDependencies.authWebSocket.registerKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN)
    AppDependencies.unauthWebSocket.registerKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN)
  }

  fun shutdown(fromTimeout: Boolean = false) {
    Log.v(TAG, "shutdown")

    previousNotificationDisposable.dispose()
    NotificationManagerCompat.from(application).cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_MICROPHONE_PERMISSION)

    uncaughtExceptionHandlerManager?.unregister()
    uncaughtExceptionHandlerManager = null

    signalAudioManager?.shutdown()
    signalAudioManager = null

    unregisterNetworkReceiver()
    unregisterPowerButtonReceiver()
    unregisterPstnCallAnsweredListener()
    AppForegroundObserver.removeListener(foregroundListener)

    AppDependencies.authWebSocket.removeKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN)
    AppDependencies.unauthWebSocket.removeKeepAliveToken(WEBSOCKET_KEEP_ALIVE_TOKEN)

    if (!ActiveCallForegroundService.stop(application, fromTimeout) && previousNotificationId != -1) {
      NotificationManagerCompat.from(application).cancel(previousNotificationId)
    }
  }

  @SuppressLint("MissingPermission")
  fun update(type: Int, recipientId: RecipientId, isVideoCall: Boolean, usesTelecom: Boolean) {
    Log.i(TAG, "update $type $recipientId $isVideoCall usesTelecom: $usesTelecom")
    this.usesTelecom = usesTelecom
    previousNotificationDisposable.dispose()

    val notificationId = CallNotificationBuilder.getNotificationId(type, usesTelecom)

    if (previousNotificationId != notificationId && previousNotificationId != -1) {
      NotificationManagerCompat.from(application).cancel(previousNotificationId)
    } else if (type == CallNotificationBuilder.TYPE_INCOMING_RINGING && previousNotificationType == CallNotificationBuilder.TYPE_INCOMING_CONNECTING) {
      Log.i(TAG, "Cancelling connecting notification so ringing is posted as a new notification")
      NotificationManagerCompat.from(application).cancel(notificationId)
    }

    previousNotificationId = notificationId
    previousNotificationType = type

    if (type == CallNotificationBuilder.TYPE_INCOMING_RINGING || type == CallNotificationBuilder.TYPE_INCOMING_CONNECTING) {
      val notification = CallNotificationBuilder.getCallInProgressNotification(application, type, Recipient.resolved(recipientId), isVideoCall, usesTelecom, requiresAsyncNotificationLoad)
      NotificationManagerCompat.from(application).notify(notificationId, notification)

      if (requiresAsyncNotificationLoad) {
        previousNotificationDisposable = Single.fromCallable { CallNotificationBuilder.getCallInProgressNotification(application, type, Recipient.resolved(recipientId), isVideoCall, usesTelecom, false) }
          .subscribeOn(Schedulers.io())
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy { asyncNotification ->
            if (NotificationManagerCompat.from(application).activeNotifications.any { n -> n.id == notificationId }) {
              NotificationManagerCompat.from(application).notify(notificationId, asyncNotification)
            }
          }
      }
    } else {
      ActiveCallForegroundService.update(application, type, recipientId, isVideoCall, usesTelecom)
      ThreadUtil.runOnMain {
        if (!shouldListenForPstnCallAnswered) {
          shouldListenForPstnCallAnswered = true
          registerPstnCallAnsweredListener()
        }
      }
    }
  }

  private fun retryPstnCallAnsweredListener() {
    ThreadUtil.runOnMain { registerPstnCallAnsweredListener() }
  }

  fun sendAudioCommand(audioCommand: AudioManagerCommand) {
    val existingAudioManager = signalAudioManager
    if (existingAudioManager != null && audioCommand is AudioManagerCommand.Initialize && audioCommand.usesTelecom != (existingAudioManager is TelecomAudioManager)) {
      Log.i(TAG, "Audio manager type does not match usesTelecom: ${audioCommand.usesTelecom}, recreating")
      val shutdownLatch = CountDownLatch(1)
      existingAudioManager.shutdown { shutdownLatch.countDown() }
      if (!shutdownLatch.await(1, TimeUnit.SECONDS)) {
        Log.w(TAG, "Timed out waiting for the previous audio manager to shut down, abandoning it")
        existingAudioManager.abandon()
      }
      if (audioCommand.usesTelecom && Build.VERSION.SDK_INT >= 31) {
        Log.i(TAG, "Clearing communication device left by the previous audio manager")
        AppDependencies.androidCallAudioManager.clearCommunicationDevice()
      }
      signalAudioManager = null
    }

    if (signalAudioManager == null) {
      val usesTelecom = if (audioCommand is AudioManagerCommand.Initialize) {
        audioCommand.usesTelecom
      } else {
        Log.w(TAG, "First AudioCommand received was not Initialize, skipping Telecom usage, command: ${audioCommand.javaClass.simpleName}")
        false
      }

      signalAudioManager = create(context = application, eventListener = this, usesTelecom = usesTelecom)
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
      @Suppress("DEPRECATION")
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
    if (powerButtonReceiver == null) {
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

  @MainThread
  private fun registerPstnCallAnsweredListener() {
    if (pstnCallAnsweredListener != null || !shouldListenForPstnCallAnswered || usesTelecom) {
      return
    }

    val listener = HangUpRtcOnPstnCallAnsweredListener()
    try {
      @Suppress("DEPRECATION")
      TelephonyUtil.getManager(application).listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
      pstnCallAnsweredListener = listener
    } catch (e: SecurityException) {
      Log.w(TAG, "Failed to listen to PSTN call answers!", e)
    }
  }

  private fun unregisterPstnCallAnsweredListener() {
    ThreadUtil.runOnMain {
      shouldListenForPstnCallAnswered = false
      pstnCallAnsweredListener?.let {
        @Suppress("DEPRECATION")
        TelephonyUtil.getManager(application).listen(it, PhoneStateListener.LISTEN_NONE)
      }
      pstnCallAnsweredListener = null
    }
  }

  override fun onAudioDeviceChanged(activeDevice: SignalAudioManager.AudioDevice, devices: Set<SignalAudioManager.AudioDevice>) {
    callManager.onAudioDeviceChanged(activeDevice, devices)
  }

  override fun onAudioDeviceChangeFailed() {
    callManager.onAudioDeviceChangeFailed()
  }

  override fun onAudioReadyForAccept() {
    callManager.onAudioReadyForAccept()
  }

  override fun onBluetoothPermissionDenied() {
    callManager.onBluetoothPermissionDenied()
  }

  override fun onMicrophoneSilencedChanged(silenced: Boolean) {
    callManager.onMicrophoneSilencedChanged(silenced)
  }

  @Suppress("deprecation")
  private class HangUpRtcOnPstnCallAnsweredListener : PhoneStateListener() {
    @Deprecated("Deprecated in Java")
    override fun onCallStateChanged(state: Int, phoneNumber: String) {
      super.onCallStateChanged(state, phoneNumber)
      if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
        Log.i(TAG, "Device phone call ended Signal call.")
        AppDependencies.signalCallManager.localHangup()
      }
    }
  }

  /** Foreground service started only after a call is established */
  class ActiveCallForegroundService : SafeForegroundService() {
    companion object {
      private const val TAG = "ActiveCallService"

      private const val EXTRA_RECIPIENT_ID = "RECIPIENT_ID"
      private const val EXTRA_IS_VIDEO_CALL = "IS_VIDEO_CALL"
      private const val EXTRA_TYPE = "TYPE"
      private const val EXTRA_SCREEN_SHARING = "SCREEN_SHARING"
      private const val EXTRA_USES_TELECOM = "USES_TELECOM"

      private val serviceLock = ReentrantLock()

      @Volatile
      private var lastExtras: Bundle? = null

      private val isServiceRequested: Boolean
        get() = lastExtras != null

      @Volatile
      var isServiceTypeDegraded: Boolean = false
        private set

      @JvmStatic
      @JvmOverloads
      fun update(context: Context, @CallNotificationBuilder.CallNotificationType type: Int, recipientId: RecipientId, isVideoCall: Boolean, usesTelecom: Boolean, isScreenSharing: Boolean? = null) {
        serviceLock.withLock {
          val extras = bundleOf(
            EXTRA_TYPE to type,
            EXTRA_RECIPIENT_ID to recipientId,
            EXTRA_IS_VIDEO_CALL to isVideoCall,
            EXTRA_USES_TELECOM to usesTelecom,
            EXTRA_SCREEN_SHARING to (isScreenSharing ?: lastExtras?.getBoolean(EXTRA_SCREEN_SHARING) ?: false)
          )

          lastExtras = extras
          isServiceTypeDegraded = false

          if (!SafeForegroundService.update(context, ActiveCallForegroundService::class.java, extras)) {
            if (!SafeForegroundService.start(context, ActiveCallForegroundService::class.java, extras)) {
              throw UnableToStartException(Exception())
            }
          }
        }
      }

      private fun updateIfActive(context: Context, type: Int, recipientId: RecipientId, isVideoCall: Boolean, usesTelecom: Boolean) {
        serviceLock.withLock {
          if (!isServiceRequested) {
            Log.i(TAG, "Async notification loaded after the service was stopped, ignoring")
            return
          }
          update(context, type, recipientId, isVideoCall, usesTelecom)
        }
      }

      fun refreshServiceType(context: Context, reason: String) {
        SignalExecutors.BOUNDED.execute {
          serviceLock.withLock {
            val extras = lastExtras ?: return@execute
            Log.i(TAG, "Refreshing service type, reason: $reason degraded: $isServiceTypeDegraded")
            isServiceTypeDegraded = false
            NotificationManagerCompat.from(context).cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_MICROPHONE_PERMISSION)
            if (!SafeForegroundService.update(context, ActiveCallForegroundService::class.java, extras)) {
              Log.i(TAG, "Service not running, starting it")
              SafeForegroundService.start(context, ActiveCallForegroundService::class.java, extras)
            }
          }
        }
      }

      fun stop(context: Context, fromTimeout: Boolean = false): Boolean {
        serviceLock.withLock {
          lastExtras = null
          isServiceTypeDegraded = false
          return SafeForegroundService.stop(context, ActiveCallForegroundService::class.java, fromTimeout)
        }
      }
    }

    private var isScreenSharing: Boolean = false
    private var lastServiceType: Int = 0
    private var hasPromptedForDegradedService = false

    override val tag: String
      get() = TAG

    override val notificationId: Int
      get() = CallNotificationBuilder.WEBRTC_NOTIFICATION

    private var notificationDisposable: Disposable = Disposable.disposed()

    @Volatile
    private var asyncServiceNotification: Notification? = null

    @Volatile
    private var lastAsyncServiceNotificationRequestTime: Long = 0

    @Volatile
    private var lastAsyncServiceNotificationType: Int = -1

    override fun onServiceStopCommandReceived(intent: Intent) {
      notificationDisposable.dispose()
      NotificationManagerCompat.from(this).cancel(CallNotificationBuilder.WEBRTC_NOTIFICATION_MICROPHONE_PERMISSION)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
      Log.w(TAG, "ActiveCallForegroundService has timed out. Hanging up. startId: $startId, foregroundServiceType: $fgsType")
      AppDependencies.signalCallManager.localHangup()
      activeCallManagerLock.withLock {
        activeCallManager?.shutdown(fromTimeout = true)
        activeCallManager = null
      }
    }

    @RequiresApi(30)
    override fun serviceType(intent: Intent): Int {
      if (SafeForegroundService.isStopping(intent) && lastServiceType != 0) {
        return lastServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION.inv()
      }

      var type = baseServiceType(intent)

      if (Permissions.hasAll(this, Manifest.permission.RECORD_AUDIO)) {
        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      }

      if (Permissions.hasAll(this, Manifest.permission.CAMERA)) {
        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
      }

      if (intent.getBooleanExtra(EXTRA_SCREEN_SHARING, false)) {
        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      }

      Log.i(TAG, "serviceType: ${describeServiceType(type)} degraded: $isServiceTypeDegraded")
      lastServiceType = type
      return type
    }

    @RequiresApi(30)
    private fun describeServiceType(type: Int): String {
      val names = listOf(
        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL to "phoneCall",
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC to "dataSync",
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE to "microphone",
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA to "camera",
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION to "mediaProjection"
      )
      return names.filter { (flag, _) -> type and flag != 0 }.joinToString("|") { it.second }.ifEmpty { "none" }
    }

    @RequiresApi(30)
    private fun baseServiceType(intent: Intent): Int {
      return if (intent.getBooleanExtra(EXTRA_USES_TELECOM, false)) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
      } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      }
    }

    @RequiresApi(30)
    override fun fallbackServiceType(intent: Intent): Int {
      val type = baseServiceType(intent)

      Log.w(TAG, "Call service type rejected by the system, falling back to $type. Microphone and camera may be blocked until the app is opened.")
      lastServiceType = type
      isServiceTypeDegraded = !SafeForegroundService.isStopping(intent)
      if (isServiceTypeDegraded && isServiceRequested && !hasPromptedForDegradedService && Permissions.hasAll(this, Manifest.permission.RECORD_AUDIO)) {
        hasPromptedForDegradedService = true
        promptToOpenCallScreen(this, R.string.CallNotificationBuilder__the_other_person_cant_hear_you_until_you_open_signal)
      }
      return type
    }

    override fun getForegroundNotification(intent: Intent): Notification {
      notificationDisposable.dispose()

      if (SafeForegroundService.isStopping(intent)) {
        Log.v(TAG, "Service is stopping, using generic stopping notification")
        return CallNotificationBuilder.getStoppingNotification(this)
      }

      if (!intent.hasExtra(EXTRA_RECIPIENT_ID) || !intent.hasExtra(EXTRA_TYPE)) {
        Log.w(TAG, "Missing required data, service is stopping, using generic stopping notification")
        return CallNotificationBuilder.getStoppingNotification(this)
      }

      val type = intent.getIntExtra(EXTRA_TYPE, 0)
      val recipient: Recipient = Recipient.resolved(IntentCompat.getParcelableExtra(intent, EXTRA_RECIPIENT_ID, RecipientId::class.java)!!)
      val isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false)
      val usesTelecom = intent.getBooleanExtra(EXTRA_USES_TELECOM, false)

      if (asyncServiceNotification != null && lastAsyncServiceNotificationType == type) {
        return asyncServiceNotification!!
      }

      val requestTime = System.currentTimeMillis()
      lastAsyncServiceNotificationRequestTime = requestTime
      notificationDisposable = Single.fromCallable { createNotification(type, recipient, isVideoCall, usesTelecom, skipAvatarLoad = false) }
        .subscribeOn(Schedulers.io())
        .filter { requestTime == lastAsyncServiceNotificationRequestTime }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy { notification ->
          lastAsyncServiceNotificationType = type
          asyncServiceNotification = notification
          updateIfActive(this, type, recipient.id, isVideoCall, usesTelecom)
        }

      return createNotification(type, recipient, isVideoCall, usesTelecom, skipAvatarLoad = true)
    }

    override fun onServiceUpdateCommandReceived(intent: Intent) {
      val wasScreenSharing = isScreenSharing
      isScreenSharing = intent.getBooleanExtra(EXTRA_SCREEN_SHARING, false)

      if (isScreenSharing && !wasScreenSharing) {
        AppDependencies.signalCallManager.onScreenSharingServiceReady()
      }
    }

    private fun createNotification(type: Int, recipient: Recipient, isVideoCall: Boolean, usesTelecom: Boolean, skipAvatarLoad: Boolean): Notification {
      return CallNotificationBuilder.getCallInProgressNotification(
        this,
        type,
        recipient,
        isVideoCall,
        usesTelecom,
        skipAvatarLoad
      )
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

  @Suppress("DEPRECATION")
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
