/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import kotlin.time.Duration

class CallQualityValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    const val SURVEY_REQUEST = "callQualityValues.survey_request"
    const val IS_CALL_QUALITY_SURVEY_ENABLED = "callQualityValues.is_call_quality_survey_enabled"
    const val LAST_FAILURE_REPORT_TIME = "callQualityValues.last_failure_report_time"
    const val LAST_SURVEY_PROMPT_TIME = "callQualityValues.last_survey_prompt_time"
  }

  var surveyRequest: SubmitCallQualitySurveyRequest? by protoValue(SURVEY_REQUEST, SubmitCallQualitySurveyRequest.ADAPTER)
  var isQualitySurveyEnabled: Boolean by booleanValue(IS_CALL_QUALITY_SURVEY_ENABLED, true)
  var lastFailureReportTime: Duration? by durationValue(LAST_FAILURE_REPORT_TIME, null)
  var lastSurveyPromptTime: Duration? by durationValue(LAST_SURVEY_PROMPT_TIME, null)

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()
}
