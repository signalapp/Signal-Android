/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.olddevice

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.registration.olddevice.preparedevice.PrepareDeviceScreen
import org.thoughtcrime.securesms.registration.olddevice.transferaccount.TransferAccountScreen

/**
 * Navigation routes for the transfer account flow.
 */
@Parcelize
sealed interface TransferAccountRoute : NavKey, Parcelable {
  @Serializable
  data object Transfer : TransferAccountRoute

  @Serializable
  data object PrepareDevice : TransferAccountRoute

  @Serializable
  data object Done : TransferAccountRoute
}

/**
 * Navigation host for the transfer account flow.
 */
@Composable
fun TransferAccountNavHost(
  viewModel: QuickTransferOldDeviceViewModel,
  modifier: Modifier = Modifier,
  onFinished: () -> Unit
) {
  val backStack by viewModel.backStack.collectAsStateWithLifecycle()

  val entryProvider = entryProvider {
    navigationEntries(
      viewModel = viewModel,
      onFinished = onFinished
    )
  }

  val decorators = listOf(
    rememberSaveableStateHolderNavEntryDecorator<NavKey>()
  )

  val entries = rememberDecoratedNavEntries(
    backStack = backStack,
    entryDecorators = decorators,
    entryProvider = entryProvider
  )

  NavDisplay(
    entries = entries,
    onBack = { viewModel.goBack() },
    modifier = modifier,
    transitionSpec = TransitionSpecs.HorizontalSlide.transitionSpec,
    popTransitionSpec = TransitionSpecs.HorizontalSlide.popTransitionSpec,
    predictivePopTransitionSpec = TransitionSpecs.HorizontalSlide.predictivePopTransitonSpec
  )
}

private fun EntryProviderScope<NavKey>.navigationEntries(
  viewModel: QuickTransferOldDeviceViewModel,
  onFinished: () -> Unit
) {
  entry<TransferAccountRoute.Transfer> {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TransferAccountScreen(
      state = state,
      emitter = { viewModel.onEvent(it) }
    )
  }
  entry<TransferAccountRoute.PrepareDevice> {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PrepareDeviceScreen(
      state = state,
      emitter = { viewModel.onEvent(it) }
    )
  }
  entry<TransferAccountRoute.Done> {
    LaunchedEffect(Unit) {
      onFinished()
    }
  }
}
