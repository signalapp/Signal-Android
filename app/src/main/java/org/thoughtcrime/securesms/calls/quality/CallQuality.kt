/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import okio.ByteString.Companion.toByteString
import org.signal.ringrtc.CallSummary
import org.signal.ringrtc.GroupCall
import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.LocaleRemoteConfig
import org.thoughtcrime.securesms.util.RemoteConfig
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Helper object for dealing with call quality ux
 */
object CallQuality {

  private val errors = listOf(
    "internalFailure",
    "signalingFailure",
    "connectionFailure",
    "iceFailedAfterConnected"
  )

  @JvmStatic
  fun handleOneToOneCallSummary(callSummary: CallSummary, hasRemoteVideoContent: Boolean) {
    val callType = if (hasRemoteVideoContent) CallType.DIRECT_VIDEO else CallType.DIRECT_VOICE

    handleCallSummary(callSummary, callType)
  }

  @JvmStatic
  fun handleGroupCallSummary(callSummary: CallSummary, kind: GroupCall.Kind) {
    val callType = when (kind) {
      GroupCall.Kind.SIGNAL_GROUP -> CallType.GROUP
      GroupCall.Kind.CALL_LINK -> CallType.CALL_LINK
    }

    handleCallSummary(callSummary, callType)
  }

  @JvmStatic
  private fun handleCallSummary(callSummary: CallSummary, callType: CallType) {
    if (isCallQualitySurveyRequired(callSummary)) {
      SignalStore.callQuality.surveyRequest = SubmitCallQualitySurveyRequest.Builder()
        .call_type(callType.code)
        .start_timestamp(callSummary.startTime)
        .end_timestamp(callSummary.endTime)
        .success(isSuccess(callSummary))
        .call_end_reason(callSummary.callEndReasonText)
        .connection_rtt_median(callSummary.qualityStats.rttMedianConnectionMillis)
        .audio_rtt_median(callSummary.qualityStats.audioStats.rttMedianMillis)
        .video_rtt_median(callSummary.qualityStats.videoStats.rttMedianMillis)
        .audio_recv_jitter_median(callSummary.qualityStats.audioStats.jitterMedianRecvMillis)
        .video_recv_jitter_median(callSummary.qualityStats.videoStats.jitterMedianRecvMillis)
        .audio_send_jitter_median(callSummary.qualityStats.audioStats.jitterMedianSendMillis)
        .video_send_jitter_median(callSummary.qualityStats.videoStats.jitterMedianSendMillis)
        .audio_send_packet_loss_fraction(callSummary.qualityStats.audioStats.packetLossFractionSend)
        .video_send_packet_loss_fraction(callSummary.qualityStats.videoStats.packetLossFractionSend)
        .audio_recv_packet_loss_fraction(callSummary.qualityStats.audioStats.packetLossFractionRecv)
        .video_recv_packet_loss_fraction(callSummary.qualityStats.videoStats.packetLossFractionRecv)
        .call_telemetry(callSummary.rawStats?.toByteString())
        .build()
    } else {
      SignalStore.callQuality.surveyRequest = null
    }
  }

  /**
   * Consumes any pending request. We will automatically filter out requests if they're over five minutes old.
   */
  fun consumeQualityRequest(): SubmitCallQualitySurveyRequest? {
    val request = SignalStore.callQuality.surveyRequest ?: return null
    SignalStore.callQuality.surveyRequest = null

    val fiveMinutesAgo = System.currentTimeMillis().milliseconds - 5.minutes
    return if (!isFeatureEnabled() || request.end_timestamp.milliseconds < fiveMinutesAgo) {
      null
    } else {
      request
    }
  }

  private fun isCallQualitySurveyRequired(callSummary: CallSummary): Boolean {
    if (!isFeatureEnabled() || !callSummary.isSurveyCandidate) {
      return false
    }

    val isSuccess = isSuccess(callSummary)
    val now = System.currentTimeMillis().milliseconds
    val lastFailure = SignalStore.callQuality.lastFailureReportTime ?: 0.milliseconds
    val failureDelta = now - lastFailure

    if (!isSuccess && (failureDelta < 1.days)) {
      SignalStore.callQuality.lastFailureReportTime = now
      return true
    }

    if (isSuccess) {
      val lastSurveyPromptTime = SignalStore.callQuality.lastSurveyPromptTime ?: 0.milliseconds
      val lastSurveyPromptDelta = now - lastSurveyPromptTime
      val lastPromptWasTooRecent = lastSurveyPromptDelta < 1.days

      if (lastPromptWasTooRecent) {
        return false
      }

      val chance = LocaleRemoteConfig.getCallQualitySurveyPartsPerMillion()
      val roll = (0 until 1_000_000).random()

      if (roll < chance) {
        return true
      }
    }

    return false
  }

  private fun isSuccess(callSummary: CallSummary): Boolean {
    return callSummary.callEndReasonText !in errors
  }

  private fun isFeatureEnabled(): Boolean {
    return RemoteConfig.callQualitySurvey && SignalStore.callQuality.isQualitySurveyEnabled
  }

  private enum class CallType(val code: String) {
    // "direct_voice", "direct_video", "group", and "call_link".
    DIRECT_VOICE("direct_voice"),
    DIRECT_VIDEO("direct_video"),
    GROUP("group"),
    CALL_LINK("call_link")
  }
}
