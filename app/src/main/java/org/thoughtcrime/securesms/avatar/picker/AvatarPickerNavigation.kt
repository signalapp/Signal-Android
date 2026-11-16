/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.LocalResultEventBus
import org.signal.core.ui.navigation.NavRouter
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.avatar.text.TextAvatarCreationEntry
import org.thoughtcrime.securesms.avatar.vector.VectorAvatarCreationEntry

/**
 * The avatar picker and the editors it opens, on [router]'s back stack, for hosts outside of app settings.
 *
 * @param viewModelFactory picks whose avatar is being chosen.
 * @param onResult called with what the user picked, just before the picker exits.
 * @param onClose called when the picker navigates back from the bottom of the stack.
 */
@Composable
fun AvatarPickerNavigation(
  router: NavRouter<AppSettingsRoute>,
  viewModelFactory: ViewModelProvider.Factory,
  onResult: (AvatarPickerResult) -> Unit,
  onClose: () -> Unit
) {
  val currentOnResult by rememberUpdatedState(onResult)
  val currentOnClose by rememberUpdatedState(onClose)

  LaunchedEffect(router) {
    router.exitRequests.collect { currentOnClose() }
  }

  val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit = remember(router) {
    { event ->
      when (event) {
        is AppSettingsNavigationEvent.NavigateToScreen -> router.navigateTo(event.route)
        is AppSettingsNavigationEvent.NavigateBack -> router.navigateBack()
      }
    }
  }

  val entryProvider = remember(parentEventEmitter, viewModelFactory) {
    entryProvider {
      registerAvatarPickerScreens(
        parentEventEmitter = parentEventEmitter,
        onResult = { currentOnResult(it) },
        viewModelFactory = viewModelFactory
      )
    }
  }

  val paneShift = TransitionSpecs.paneShift()
  val paneShiftPop = TransitionSpecs.paneShift(pop = true)

  CompositionLocalProvider(LocalResultEventBus provides router.resultBus) {
    NavDisplay(
      backStack = router.backStack,
      onBack = router::navigateBack,
      entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
      ),
      transitionSpec = { paneShift },
      popTransitionSpec = { paneShiftPop },
      predictivePopTransitionSpec = { paneShiftPop },
      entryProvider = entryProvider
    )
  }
}

/**
 * Registers the avatar picker and the text and vector editors it opens.
 *
 * @param onResult called with what the user picked, just before the picker navigates back.
 * @param viewModelFactory picks whose avatar is being chosen: the user's own by default, or a group's.
 */
fun EntryProviderScope<NavKey>.registerAvatarPickerScreens(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  onResult: (AvatarPickerResult) -> Unit,
  viewModelFactory: ViewModelProvider.Factory = AvatarPickerViewModel.Factory(groupId = null, isNewGroup = false, groupAvatarMedia = null)
) {
  detailEntry<AppSettingsRoute.AccountRoute.AvatarPicker> {
    AvatarPickerEntry(parentEventEmitter = parentEventEmitter, onResult = onResult, viewModelFactory = viewModelFactory)
  }

  detailEntry<AppSettingsRoute.AccountRoute.TextAvatarEditor> { route ->
    TextAvatarCreationEntry(initialAvatar = route.toAvatar(), parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AccountRoute.VectorAvatarEditor> { route ->
    VectorAvatarCreationEntry(initialAvatar = route.toAvatar(), parentEventEmitter = parentEventEmitter)
  }
}
