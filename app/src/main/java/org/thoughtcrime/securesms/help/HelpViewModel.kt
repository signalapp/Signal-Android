/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.ResourceUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository
import org.thoughtcrime.securesms.util.SupportEmailUtil

class HelpViewModel(
  startCategoryIndex: Int,
  application: Application
) : AndroidViewModel(application) {

  private val internalState = MutableStateFlow(
    HelpScreenState(
      categoryIndex = startCategoryIndex
    )
  )
  val state = internalState.asStateFlow()

  private val internalSideEffect = Channel<HelpScreenSideEffects>(Channel.BUFFERED)
  val sideEffect = internalSideEffect.receiveAsFlow()

  private val submitDebugLogRepository = SubmitDebugLogRepository()

  fun onEvent(event: HelpScreenEvents) {
    when (event) {
      is HelpScreenEvents.ProblemTextChanged -> onProblemChanged(event.text)
      is HelpScreenEvents.CategorySelected -> onCategorySelected(event.index)
      is HelpScreenEvents.FeelingSelected -> onFeelingSelected(event.feeling)
      is HelpScreenEvents.DebugLogsToggled -> onDebugLogsToggled(event.toggle)
      is HelpScreenEvents.OnNextClick -> onNextClick()
      else -> error("Unhandled event: $event")
    }
  }

  fun onScreenResumed() {
    internalState.update { it.copy(isSubmitting = false) }
  }

  private fun onProblemChanged(text: String) {
    internalState.update { it.copy(problemText = text) }
  }

  private fun onCategorySelected(index: Int) {
    internalState.update { it.copy(categoryIndex = index) }
  }

  private fun onFeelingSelected(feeling: Feeling) {
    internalState.update { current ->
      current.copy(selectedFeeling = if (current.selectedFeeling == feeling) null else feeling)
    }
  }

  private fun onDebugLogsToggled(include: Boolean) {
    internalState.update { it.copy(includeDebugLog = include) }
  }

  private fun onNextClick() {
    if (!state.value.isFormValid) {
      internalState.update { it.copy(displayValidationErrors = true) }
      viewModelScope.launch {
        if (state.value.categoryIndex <= 0) {
          internalSideEffect.send(HelpScreenSideEffects.ShakeCategory)
        }
        internalSideEffect.send(HelpScreenSideEffects.ShowSnackbar(R.string.HelpFragment__please_be_as_descriptive_as_possible))
      }
      return
    }

    viewModelScope.launch {
      if (internalState.value.includeDebugLog) {
        internalState.update { it.copy(isSubmitting = true) }

        submitDebugLogRepository.buildAndSubmitLog { optionalUrl ->
          val debugLogUrl = if (optionalUrl.isPresent) optionalUrl.get()
          else application.getString(R.string.HelpFragment__could_not_upload_logs)

          dispatchEmail(debugLogUrl)
        }
      } else {
        dispatchEmail(debugLogUrl = null)
      }
    }
  }

  private fun dispatchEmail(debugLogUrl: String?) {
    val context = application
    val state = internalState.value
    val englishCategories: Array<String> = ResourceUtil.getEnglishResources(context)
      .getStringArray(R.array.HelpFragment__categories_6)
    val categoryLabel = englishCategories.getOrElse(state.categoryIndex) { "" }

    val suffix = buildString {
      if (debugLogUrl != null) {
        append("\n")
        append(context.getString(R.string.HelpFragment__debug_log))
        append(" ")
        append(debugLogUrl)
      }
      state.selectedFeeling?.let { feeling ->
        append("\n\n")
        append(feeling.emojiCode)
        append("\n")
        append(context.getString(feeling.labelRes))
      }
    }

    val subject = context.getString(R.string.HelpFragment__signal_android_support_request)
    val body = SupportEmailUtil.generateSupportEmailBody(
      context,
      R.string.HelpFragment__signal_android_support_request,
      " - $categoryLabel",
      "${state.problemText}\n\n",
      suffix
    )

    viewModelScope.launch {
      internalSideEffect.send(HelpScreenSideEffects.OpenEmail(subject = subject, body = body))
      internalState.update { it.copy(isSubmitting = false) }
    }
  }
}
