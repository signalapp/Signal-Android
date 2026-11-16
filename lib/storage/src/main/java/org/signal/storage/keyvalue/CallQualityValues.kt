/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.storage.keyvalue

import org.signal.storage.protos.CallQualitySurveyRequest
import kotlin.time.Duration

class CallQualityValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    const val SURVEY_REQUEST = "callQualityValues.survey_request"
    const val IS_CALL_QUALITY_SURVEY_ENABLED = "callQualityValues.is_call_quality_survey_enabled"
    const val LAST_FAILURE_REPORT_TIME = "callQualityValues.last_failure_report_time"
    const val LAST_SURVEY_PROMPT_TIME = "callQualityValues.last_survey_prompt_time"
  }

  var surveyRequest: CallQualitySurveyRequest? by protoValue(SURVEY_REQUEST, CallQualitySurveyRequest.ADAPTER)
  var isQualitySurveyEnabled: Boolean by booleanValue(IS_CALL_QUALITY_SURVEY_ENABLED, true)
  var lastFailureReportTime: Duration? by durationValue(LAST_FAILURE_REPORT_TIME, null)
  var lastSurveyPromptTime: Duration? by durationValue(LAST_SURVEY_PROMPT_TIME, null)

  public override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String>
    get() = emptyList()
}
