/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.signal.storageservice.protos.calls.quality.SubmitCallQualitySurveyRequest
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobs.protos.CallQualitySurveySubmissionJobData
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository
import org.whispersystems.signalservice.api.NetworkResult
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.hours

/**
 * Job which will upload call quality survey data after submission by user.
 */
class CallQualitySurveySubmissionJob private constructor(
  private var request: SubmitCallQualitySurveyRequest,
  private val includeDebugLogs: Boolean,
  parameters: Parameters
) : Job(parameters) {

  constructor(request: SubmitCallQualitySurveyRequest, includeDebugLogs: Boolean) : this(
    request,
    includeDebugLogs,
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setLifespan(6.hours.inWholeMilliseconds)
      .build()
  )

  companion object {
    private val TAG = Log.tag(CallQualitySurveySubmissionJob::class)

    const val KEY = "CallQualitySurveySubmission"
  }

  override fun serialize(): ByteArray {
    return CallQualitySurveySubmissionJobData(
      request = request,
      includeDebugLogs = includeDebugLogs
    ).encode()
  }

  override fun getFactoryKey(): String = KEY

  override fun run(): Result {
    Log.i(TAG, "Starting call quality survey submission.")

    val request = handleRequestDebugLogs(request)

    Log.i(TAG, "Sending survey data to server.")

    return submitRequestData(request)
  }

  override fun onFailure() = Unit

  private fun handleRequestDebugLogs(request: SubmitCallQualitySurveyRequest): SubmitCallQualitySurveyRequest {
    return if (includeDebugLogs) {
      Log.i(TAG, "Including debug logs.")

      if (request.debug_log_url != null) {
        Log.i(TAG, "Already included debug logs.")
        request
      } else {
        uploadDebugLogs(request)
      }
    } else {
      Log.i(TAG, "Skipping debug logs.")
      request
    }
  }

  private fun uploadDebugLogs(request: SubmitCallQualitySurveyRequest): SubmitCallQualitySurveyRequest {
    val repository = SubmitDebugLogRepository()
    val url = repository.buildAndSubmitLogSync(request.end_timestamp).getOrNull()

    if (url == null) {
      Log.w(TAG, "Failed to upload debug log. Proceeding with survey capture.")
    }

    return request.newBuilder().debug_log_url(url).build()
  }

  private fun submitRequestData(request: SubmitCallQualitySurveyRequest): Result {
    return when (val networkResult = AppDependencies.callingApi.submitCallQualitySurvey(request)) {
      is NetworkResult.Success -> {
        Log.i(TAG, "Successfully submitted survey.")
        Result.success()
      }
      is NetworkResult.ApplicationError -> {
        Log.w(TAG, "Failed to submit due to an application error.", networkResult.getCause())
        Result.failure()
      }
      is NetworkResult.NetworkError -> {
        Log.w(TAG, "Failed to submit due to a network error. Scheduling a retry.", networkResult.getCause())
        Result.retry(defaultBackoff())
      }
      is NetworkResult.StatusCodeError -> {
        when (networkResult.code) {
          429 -> {
            val retryIn: Long = networkResult.header("Retry-After")?.toLongOrNull()?.let { it * 1000 } ?: defaultBackoff()
            Log.w(TAG, "Failed to submit survey due to rate limit, status code ${networkResult.code} retrying in ${retryIn}ms", networkResult.getCause())
            Result.retry(retryIn)
          }

          else -> {
            Log.w(TAG, "Failed to submit survey, status code ${networkResult.code}", networkResult.getCause())
            Result.failure()
          }
        }
      }
    }
  }

  class Factory : Job.Factory<CallQualitySurveySubmissionJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CallQualitySurveySubmissionJob {
      val jobData = CallQualitySurveySubmissionJobData.ADAPTER.decode(serializedData!!)

      return CallQualitySurveySubmissionJob(jobData.request!!, jobData.includeDebugLogs, parameters)
    }
  }
}
