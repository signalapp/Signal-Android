/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * The form for contacting support, with category [startCategoryIndex] preselected, wired to its view model.
 */
@Composable
fun HelpEntry(
  startCategoryIndex: Int,
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val context = LocalContext.current
  val categoryCount = stringArrayResource(R.array.HelpFragment__categories_6).size
  val viewModel: HelpViewModel = viewModel {
    HelpViewModel(
      startCategoryIndex = startCategoryIndex.coerceIn(0, categoryCount - 1),
      application = AppDependencies.application,
      parentEventEmitter = parentEventEmitter
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()

  LifecycleResumeEffect(viewModel) {
    viewModel.onScreenResumed()
    onPauseOrDispose {}
  }

  val faqLink = stringResource(R.string.HelpFragment__link__faq)
  val whatIsDebugLogLink = stringResource(R.string.HelpFragment__link__debug_info)

  HelpScreenContent(
    state = state,
    onEvent = { event ->
      when (event) {
        HelpScreenEvents.FAQClick -> CommunicationActions.openBrowserLink(context, faqLink)
        HelpScreenEvents.WhatIsDebugLogClick -> CommunicationActions.openBrowserLink(context, whatIsDebugLogLink)
        else -> viewModel.onEvent(event)
      }
    },
    sideEffect = viewModel.sideEffect
  )
}
