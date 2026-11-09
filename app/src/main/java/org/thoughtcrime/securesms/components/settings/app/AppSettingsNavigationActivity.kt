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
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.signal.appsettings.routes.AppSettingsNavigation
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.core.ui.compose.WindowOverlayHost
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.getParcelableExtraCompat
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerResult
import org.thoughtcrime.securesms.avatar.picker.registerAvatarPickerScreens
import org.thoughtcrime.securesms.badges.self.registerBadgeScreens
import org.thoughtcrime.securesms.blocked.BlockedUsersActivity
import org.thoughtcrime.securesms.components.enableSettingsEdgeToEdge
import org.thoughtcrime.securesms.components.settings.app.account.registerAccountRoutes
import org.thoughtcrime.securesms.components.settings.app.appearance.registerAppearanceRoutes
import org.thoughtcrime.securesms.components.settings.app.backups.registerBackupsRoutes
import org.thoughtcrime.securesms.components.settings.app.chats.folders.registerChatFoldersRoutes
import org.thoughtcrime.securesms.components.settings.app.chats.registerChatsRoutes
import org.thoughtcrime.securesms.components.settings.app.help.registerHelpRoutes
import org.thoughtcrime.securesms.components.settings.app.internal.registerInternalRoutes
import org.thoughtcrime.securesms.components.settings.app.invite.registerInviteRoutes
import org.thoughtcrime.securesms.components.settings.app.labs.registerLabsRoutes
import org.thoughtcrime.securesms.components.settings.app.notifications.registerNotificationsRoutes
import org.thoughtcrime.securesms.components.settings.app.privacy.registerPrivacyRoutes
import org.thoughtcrime.securesms.components.settings.app.storage.registerDataAndStorageRoutes
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayComponent
import org.thoughtcrime.securesms.components.settings.app.subscription.GooglePayRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.registerDonationsRoutes
import org.thoughtcrime.securesms.components.settings.app.updates.registerAppUpdatesRoutes
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.registerUsernameLinkScreens
import org.thoughtcrime.securesms.devicetransfer.olddevice.OldDeviceTransferActivity
import org.thoughtcrime.securesms.linkdevice.registerLinkDeviceRoutes
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.main.EmptyDetailScreen
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity
import org.thoughtcrime.securesms.profiles.manage.editprofile.registerEditProfileScreens
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperActivity

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
      router.openDirectly(startRoute)
    }

    setContent {
      SignalTheme {
        WindowOverlayHost {
          AppSettingsNavigation(
            router = router,
            onClose = ::finish,
            home = { selectedRoute -> AppSettingsEntry(parentEventEmitter = router::onEvent, selectedRoute = selectedRoute) },
            emptyDetailContent = { EmptyDetailScreen() },
            defaultDetailRoute = AppSettingsRoute.AccountRoute.ManageProfile,
            tryLaunchActivity = ::tryLaunchActivity,
            detailEntries = {
              registerAccountRoutes(router::onEvent)
              registerAppearanceRoutes(router::onEvent)
              registerChatsRoutes(router::onEvent)
              registerChatFoldersRoutes(router::onEvent)
              registerDonationsRoutes(router::onEvent)
              registerNotificationsRoutes(router::onEvent)
              registerLabsRoutes(router::onEvent)
              registerAppUpdatesRoutes(router::onEvent)
              registerHelpRoutes(router::onEvent)
              registerInviteRoutes(router::onEvent)
              registerPrivacyRoutes(router::onEvent)
              registerDataAndStorageRoutes(router::onEvent)
              registerBackupsRoutes(router::onEvent)
              registerEditProfileScreens(router::onEvent)
              registerAvatarPickerScreens(router::onEvent, onResult = { router.resultBus.sendResult<AvatarPickerResult>(result = it) })
              registerBadgeScreens(router::onEvent)
              registerUsernameLinkScreens(router::onEvent)
              registerInternalRoutes(router::onEvent)
              registerLinkDeviceRoutes(router::onEvent)
            }
          )
        }
      }
    }

    recreateOnThemeChange()
    performActionOnCreate(savedInstanceState)
  }

  private fun tryLaunchActivity(route: AppSettingsRoute): Boolean {
    val intent = when (route) {
      AppSettingsRoute.Payments -> Intent(this, PaymentsActivity::class.java)
      AppSettingsRoute.AppearanceRoute.Wallpaper -> ChatWallpaperActivity.createIntent(this)
      AppSettingsRoute.PrivacyRoute.BlockedUsers -> Intent(this, BlockedUsersActivity::class.java)
      AppSettingsRoute.AccountRoute.OldDeviceTransfer -> Intent(this, OldDeviceTransferActivity::class.java)
      AppSettingsRoute.HelpRoute.DebugLog -> Intent(this, SubmitDebugLogActivity::class.java)
      else -> return false
    }

    startActivity(intent)
    return true
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
