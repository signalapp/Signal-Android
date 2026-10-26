/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.appsettings.routes.AppSettingsNavigation
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.getParcelableExtraCompat
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.components.enableSettingsEdgeToEdge
import org.thoughtcrime.securesms.components.settings.app.appearance.registerAppearanceRoutes
import org.thoughtcrime.securesms.components.settings.app.chats.registerChatsRoutes
import org.thoughtcrime.securesms.components.settings.app.privacy.registerPrivacyRoutes
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.main.EmptyDetailScreen
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Hosts app settings in [AppSettingsNavigation] rather than the fragment nav graph, gated by
 * [RemoteConfig.useAppSettingsNavigation].
 */
class AppSettingsNavigationActivity : PassphraseRequiredActivity(), GooglePayComponent {

  companion object {
    private const val START_ROUTE = "app.settings.navigation.args.START_ROUTE"

    fun createIntent(context: Context, startRoute: AppSettingsRoute): Intent {
      return Intent(context, AppSettingsNavigationActivity::class.java)
        .putExtra(START_ROUTE, startRoute)
    }
  }

  override val googlePayRepository: GooglePayRepository by lazy { GooglePayRepository(this) }
  override val googlePayResultPublisher: Subject<GooglePayComponent.GooglePayResult> = PublishSubject.create()

  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()
  private val router: AppSettingsRouter by viewModels()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableSettingsEdgeToEdge()
    super.onCreate(savedInstanceState, ready)
    dynamicTheme.onCreate(this)

    val startRoute = intent.getParcelableExtraCompat(START_ROUTE, AppSettingsRoute::class.java)
    if (savedInstanceState == null && startRoute != null && startRoute != AppSettingsRoute.Empty) {
      router.navigator.processEvent(ListDetailEvents.Push(startRoute))
    }

    setContent {
      SignalTheme {
        AppSettingsNavigation(
          router = router,
          onClose = ::finish,
          home = { selectedRoute -> AppSettingsEntry(parentEventEmitter = router::onEvent, selectedRoute = selectedRoute) },
          emptyDetailContent = { EmptyDetailScreen() },
          detailEntries = {
            registerAppearanceRoutes(router::onEvent)
            registerChatsRoutes(router::onEvent)
            registerPrivacyRoutes(router::onEvent)

            // TODO Temporary escape hatch while most destinations are unregistered here; remove before the flag ships.
            detailEntry<AppSettingsRoute.InternalRoute.Internal> {
              LaunchedEffect(Unit) { disableAppSettingsNavigation() }
            }
          }
        )
      }
    }

    recreateOnThemeChange()
    performActionOnCreate(savedInstanceState)
  }

  private fun disableAppSettingsNavigation() {
    lifecycleScope.launch {
      withContext(Dispatchers.Default) {
        RemoteConfig.overrides -= RemoteConfig.APP_SETTINGS_NAVIGATION_KEY
        SignalStore.internal.remoteConfigOverrides = RemoteConfig.overrides
      }

      startActivity(AppSettingsActivity.home(this@AppSettingsNavigationActivity))
      finish()
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    finish()
    startActivity(intent)
  }

  @Suppress("DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    googlePayResultPublisher.onNext(GooglePayComponent.GooglePayResult(requestCode, resultCode, data))
  }
}
