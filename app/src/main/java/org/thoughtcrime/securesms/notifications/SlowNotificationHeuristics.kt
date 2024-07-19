/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.os.Build
import android.text.TextUtils
import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.DeviceProperties
import org.thoughtcrime.securesms.util.JsonUtils
import org.thoughtcrime.securesms.util.LocaleRemoteConfig
import org.thoughtcrime.securesms.util.PowerManagerCompat
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Heuristic for estimating if a user has been experiencing issues with delayed notifications.
 *
 * This uses local metrics based off of message latency, failed service starts, and queue drain
 * timeouts.
 *
 * This will enable us to attempt to improve notifications for users who are experiencing these issues.
 */
object SlowNotificationHeuristics {

  private val TAG = Log.tag(SlowNotificationHeuristics::class.java)

  fun getConfiguration(): Configuration {
    val json = RemoteConfig.delayedNotificationsPromptConfig
    return if (TextUtils.isEmpty(json)) {
      getDefaultConfiguration()
    } else {
      try {
        JsonUtils.fromJson(json, Configuration::class.java)
      } catch (exception: Exception) {
        getDefaultConfiguration()
      }
    }
  }

  private fun getDefaultConfiguration(): Configuration {
    return Configuration(
      minimumEventAgeMs = 3.days.inWholeMilliseconds,
      minimumServiceEventCount = 10,
      serviceStartFailurePercentage = 0.5f,
      minimumMessageLatencyEvents = 50,
      weeklyFailedQueueDrains = 5,
      messageLatencyPercentiles = mapOf(
        90 to 2.hours.inWholeMilliseconds,
        50 to 30.minutes.inWholeMilliseconds
      )
    )
  }

  @JvmStatic
  fun shouldPromptUserForDelayedNotificationLogs(): Boolean {
    if (!LocaleRemoteConfig.isDelayedNotificationPromptEnabled() || SignalStore.uiHints.hasDeclinedToShareNotificationLogs()) {
      return false
    }
    if (System.currentTimeMillis() - SignalStore.uiHints.lastNotificationLogsPrompt < TimeUnit.DAYS.toMillis(7)) {
      return false
    }

    return true
  }

  @JvmStatic
  fun shouldPromptBatterySaver(): Boolean {
    if (Build.VERSION.SDK_INT < 23) {
      return false
    }

    val remoteEnabled = LocaleRemoteConfig.isBatterySaverPromptEnabled() || LocaleRemoteConfig.isDelayedNotificationPromptEnabled()
    if (!remoteEnabled || SignalStore.uiHints.hasDismissedBatterySaverPrompt()) {
      return false
    }

    if (System.currentTimeMillis() - SignalStore.uiHints.lastBatterySaverPrompt < TimeUnit.DAYS.toMillis(7)) {
      return false
    }

    return true
  }

  @WorkerThread
  @JvmStatic
  fun isHavingDelayedNotifications(): Boolean {
    if (!SignalStore.settings.isMessageNotificationsEnabled ||
      !NotificationChannels.getInstance().areNotificationsEnabled()
    ) {
      // If user does not have notifications enabled, we shouldn't bother them about delayed notifications
      return false
    }
    val configuration = getConfiguration()
    val db = LocalMetricsDatabase.getInstance(AppDependencies.application)

    val metrics = db.getMetrics()

    val failedServiceStarts = hasRepeatedFailedServiceStarts(metrics, configuration.minimumEventAgeMs, configuration.minimumServiceEventCount, configuration.serviceStartFailurePercentage)
    val failedQueueDrains = isFailingToDrainQueue(metrics, configuration.minimumEventAgeMs, configuration.weeklyFailedQueueDrains)
    val longMessageLatency = hasLongMessageLatency(metrics, configuration.minimumEventAgeMs, configuration.minimumMessageLatencyEvents, configuration.messageLatencyPercentiles)

    if (failedServiceStarts || failedQueueDrains || longMessageLatency) {
      Log.w(TAG, "User seems to be having delayed notifications: failed-service-starts=$failedServiceStarts failedQueueDrains=$failedQueueDrains longMessageLatency=$longMessageLatency")
      return true
    }
    return false
  }

  /**
   * Returns whether or not the delayed notifications may be due to battery saver optimizations.
   *
   * Some OEMs over-optimize this battery restrictions and remove network even after receiving a
   * high priority push.
   *
   * We consider a scenario where removing battery optimizations can fix delayed notifications:
   *  - Data saver must be off (or white listed), otherwise it can be causing delayed notifications
   *  - App must not already be exempted from battery optimizations
   *
   * We do not need to check if {ActivityManager#isBackgroundRestricted} is true, because if the app
   * is set to "Optimized" this will be false (and can be culprit to delayed notifications) or if
   * true can most definitely be at fault.
   */
  @JvmStatic
  fun isBatteryOptimizationsOn(): Boolean {
    val applicationContext = AppDependencies.application
    if (DeviceProperties.getDataSaverState(applicationContext) == DeviceProperties.DataSaverState.ENABLED) {
      return false
    }
    if (PowerManagerCompat.isIgnoringBatteryOptimizations(applicationContext)) {
      return false
    }
    return true
  }

  fun getDeviceSpecificShowCondition(): DeviceSpecificNotificationConfig.ShowCondition {
    return DeviceSpecificNotificationConfig.currentConfig.showCondition
  }

  fun shouldShowDeviceSpecificDialog(): Boolean {
    return LocaleRemoteConfig.isDeviceSpecificNotificationEnabled() && SignalStore.uiHints.lastSupportVersionSeen < DeviceSpecificNotificationConfig.currentConfig.version
  }

  private fun hasRepeatedFailedServiceStarts(metrics: List<LocalMetricsDatabase.EventMetrics>, minimumEventAgeMs: Long, minimumEventCount: Int, failurePercentage: Float): Boolean {
    if (!haveEnoughData(SignalLocalMetrics.FcmServiceStartSuccess.NAME, minimumEventAgeMs) && !haveEnoughData(SignalLocalMetrics.FcmServiceStartFailure.NAME, minimumEventAgeMs)) {
      Log.d(TAG, "insufficient data for service starts")
      return false
    }

    val successes = metrics.filter { it.name == SignalLocalMetrics.FcmServiceStartSuccess.NAME }
    val failures = metrics.filter { it.name == SignalLocalMetrics.FcmServiceStartFailure.NAME }

    if ((successes.size + failures.size) < minimumEventCount) {
      Log.d(TAG, "insufficient service start events")
      return false
    }

    if (failures.size / (failures.size + successes.size) >= failurePercentage) {
      Log.w(TAG, "User often unable start FCM service. ${failures.size} failed : ${successes.size} successful")
      return true
    }
    return false
  }

  private fun isFailingToDrainQueue(metrics: List<LocalMetricsDatabase.EventMetrics>, minimumEventAgeMs: Long, failureThreshold: Int): Boolean {
    if (!haveEnoughData(SignalLocalMetrics.PushWebsocketFetch.SUCCESS_EVENT, minimumEventAgeMs) && !haveEnoughData(SignalLocalMetrics.PushWebsocketFetch.TIMEOUT_EVENT, minimumEventAgeMs)) {
      Log.d(TAG, "insufficient data for failed queue drains")
      return false
    }
    val failures = metrics.filter { it.name == SignalLocalMetrics.PushWebsocketFetch.TIMEOUT_EVENT }
    if (failures.size < failureThreshold) {
      return false
    }
    Log.w(TAG, "User has repeatedly failed to drain queue ${failures.size} events")
    return true
  }

  private fun hasLongMessageLatency(metrics: List<LocalMetricsDatabase.EventMetrics>, minimumEventAgeMs: Long, messageThreshold: Int, percentiles: Map<Int, Long>): Boolean {
    if (!haveEnoughData(SignalLocalMetrics.MessageLatency.NAME_HIGH, minimumEventAgeMs)) {
      Log.d(TAG, "insufficient data for message latency")
      return false
    }
    val eventCount = metrics.find { it.name == SignalLocalMetrics.MessageLatency.NAME_HIGH }?.count ?: 0
    if (eventCount < messageThreshold) {
      Log.d(TAG, "not enough messages for message latency")
      return false
    }
    val db = LocalMetricsDatabase.getInstance(AppDependencies.application)
    for ((percentage, threshold) in percentiles.entries) {
      val averageLatency = db.eventPercent(SignalLocalMetrics.MessageLatency.NAME_HIGH, percentage.coerceAtMost(100).coerceAtLeast(0))

      if (averageLatency > threshold) {
        Log.w(TAG, "User has high average message latency of $averageLatency ms over $eventCount events over threshold of $threshold ms")
        return true
      }
    }
    return false
  }

  private fun haveEnoughData(eventName: String, minimumEventAgeMs: Long): Boolean {
    val db = LocalMetricsDatabase.getInstance(AppDependencies.application)

    val oldestEvent = db.getOldestMetricTime(eventName)

    return !(oldestEvent == 0L || oldestEvent > System.currentTimeMillis() - minimumEventAgeMs)
  }
}

data class Configuration(
  val minimumEventAgeMs: Long,
  val minimumServiceEventCount: Int,
  val serviceStartFailurePercentage: Float,
  val weeklyFailedQueueDrains: Int,
  val minimumMessageLatencyEvents: Int,
  val messageLatencyPercentiles: Map<Int, Long>
)
