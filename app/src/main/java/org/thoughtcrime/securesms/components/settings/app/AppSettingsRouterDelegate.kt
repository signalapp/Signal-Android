/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.AppSettingsRouter

/**
 * Provides the [AppSettingsRouter] a settings fragment hands its screen as the parent event emitter.
 *
 * Under [AppSettingsNavigationActivity] this is the activity's router, which applies navigation to the activity's back
 * stack, so the fragment does nothing with it. Under any other host, the fragment owns its router and turns each event
 * into nav graph navigation with [onNavigateBack] and [onNavigateToScreen] while resumed.
 */
fun Fragment.appSettingsRouter(
  onNavigateBack: () -> Unit = { requireActivity().onBackPressedDispatcher.onBackPressed() },
  onNavigateToScreen: (AppSettingsRoute) -> Unit = { error("Unexpected route: ${it.javaClass.name}") }
): Lazy<AppSettingsRouter> {
  return AppSettingsRouterDelegate(this, onNavigateBack, onNavigateToScreen)
}

private class AppSettingsRouterDelegate(
  private val fragment: Fragment,
  private val onNavigateBack: () -> Unit,
  private val onNavigateToScreen: (AppSettingsRoute) -> Unit
) : Lazy<AppSettingsRouter> {

  private val activityRouter: Lazy<AppSettingsRouter> = fragment.activityViewModels()
  private val fragmentRouter: Lazy<AppSettingsRouter> = fragment.viewModels()

  private val isActivityRouter: Boolean
    get() = fragment.requireActivity() is AppSettingsNavigationActivity

  override val value: AppSettingsRouter
    get() = if (isActivityRouter) activityRouter.value else fragmentRouter.value

  override fun isInitialized(): Boolean = activityRouter.isInitialized() || fragmentRouter.isInitialized()

  init {
    fragment.viewLifecycleOwnerLiveData.observe(fragment) { owner ->
      if (owner == null || isActivityRouter) {
        return@observe
      }

      owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
          fragmentRouter.value.events.collect { event ->
            when (event) {
              is AppSettingsNavigationEvent.NavigateBack -> onNavigateBack()
              is AppSettingsNavigationEvent.NavigateToScreen -> onNavigateToScreen(event.route)
            }
          }
        }
      }
    }
  }
}
