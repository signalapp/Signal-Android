/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerResult
import org.thoughtcrime.securesms.avatar.picker.registerAvatarPickerScreens
import org.thoughtcrime.securesms.badges.self.registerBadgeScreens
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.registerUsernameLinkScreens
import org.thoughtcrime.securesms.profiles.manage.about.EditAboutEntry
import org.thoughtcrime.securesms.profiles.manage.name.EditProfileNameEntry
import org.thoughtcrime.securesms.profiles.manage.username.UsernameEditEntry

/**
 * Edit profile and everything it opens, one screen at a time, on [router]'s back stack.
 *
 * @param onClose called when a screen asks to navigate back from the bottom of the stack.
 */
@Composable
fun EditProfileNavigation(
  router: NavRouter<AppSettingsRoute>,
  onClose: () -> Unit
) {
  val currentOnClose by rememberUpdatedState(onClose)

  LaunchedEffect(router) {
    router.exitRequests.collect { currentOnClose() }
  }

  val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit = remember(router) {
    { event ->
      when (event) {
        is AppSettingsNavigationEvent.NavigateToScreen -> router.navigateTo(event.route)
        AppSettingsNavigationEvent.NavigateBack -> router.navigateBack()
      }
    }
  }

  val entryProvider = remember(parentEventEmitter) {
    entryProvider {
      registerEditProfileScreens(parentEventEmitter)
      registerAvatarPickerScreens(parentEventEmitter, onResult = { router.resultBus.sendResult<AvatarPickerResult>(result = it) })
      registerBadgeScreens(parentEventEmitter)
      registerUsernameLinkScreens(parentEventEmitter)
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
 * Registers edit profile and the screens it opens for each part of the profile.
 */
fun EntryProviderScope<NavKey>.registerEditProfileScreens(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.AccountRoute.ManageProfile> {
    EditProfileEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AccountRoute.ProfileName> {
    EditProfileNameEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AccountRoute.About> {
    EditAboutEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.AccountRoute.Username> { route ->
    UsernameEditEntry(mode = route.mode, parentEventEmitter = parentEventEmitter)
  }
}
