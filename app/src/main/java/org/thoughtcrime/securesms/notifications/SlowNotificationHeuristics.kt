/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.util.SignalLocalMetrics

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

  fun isHavingDelayedNotifications(configuration: Configuration): Boolean {
    val db = LocalMetricsDatabase.getInstance(ApplicationDependencies.getApplication())

    val metrics = db.getMetrics()

    val failedServiceStarts = hasRepeatedFailedServiceStarts(metrics, configuration.minimumEventAgeMs, configuration.minimumServiceEventCount, configuration.serviceStartFailurePercentage)
    val failedQueueDrains = isFailingToDrainQueue(metrics, configuration.minimumEventAgeMs, configuration.weeklyFailedQueueDrains)
    val longMessageLatency = hasLongMessageLatency(metrics, configuration.minimumEventAgeMs, configuration.messageLatencyPercentage, configuration.minimumMessageLatencyEvents, configuration.messageLatencyThreshold)

    if (failedServiceStarts || failedQueueDrains || longMessageLatency) {
      Log.w(TAG, "User seems to be having delayed notifications: failed-service-starts=$failedServiceStarts failedQueueDrains=$failedQueueDrains longMessageLatency=$longMessageLatency")
      return true
    }
    return false
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

  private fun hasLongMessageLatency(metrics: List<LocalMetricsDatabase.EventMetrics>, minimumEventAgeMs: Long, percentage: Int, messageThreshold: Int, durationThreshold: Long): Boolean {
    if (!haveEnoughData(SignalLocalMetrics.MessageLatency.NAME, minimumEventAgeMs)) {
      Log.d(TAG, "insufficient data for message latency")
      return false
    }
    val eventCount = metrics.count { it.name == SignalLocalMetrics.MessageLatency.NAME }
    if (eventCount < messageThreshold) {
      Log.d(TAG, "not enough messages for message latency")
      return false
    }
    val db = LocalMetricsDatabase.getInstance(ApplicationDependencies.getApplication())
    val averageLatency = db.eventPercent(SignalLocalMetrics.MessageLatency.NAME, percentage.coerceAtMost(100).coerceAtLeast(0))

    val longMessageLatency = averageLatency > durationThreshold
    if (longMessageLatency) {
      Log.w(TAG, "User has high average message latency of $averageLatency ms over $eventCount events")
    }
    return longMessageLatency
  }

  private fun haveEnoughData(eventName: String, minimumEventAgeMs: Long): Boolean {
    val db = LocalMetricsDatabase.getInstance(ApplicationDependencies.getApplication())

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
  val messageLatencyThreshold: Long,
  val messageLatencyPercentage: Int
)
