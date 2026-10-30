/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.compose.theme.SignalTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppSettingsNavigationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val router = AppSettingsRouter(SavedStateHandle())

  private var closed = 0

  @Test
  @Config(qualifiers = "w800dp-h700dp")
  fun `a split-pane window opens the default detail`() {
    setContent(defaultDetailRoute = AppSettingsRoute.ChatsRoute.Chats)

    assertThat(router.navigator.detail.value).isEqualTo(AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  @Config(qualifiers = "w800dp-h700dp")
  fun `a split-pane window keeps the detail it was opened with`() {
    router.navigator.processEvent(ListDetailEvents.Push(AppSettingsRoute.AppearanceRoute.Appearance))

    setContent(defaultDetailRoute = AppSettingsRoute.ChatsRoute.Chats)

    assertThat(router.navigator.detail.value).isEqualTo(AppSettingsRoute.AppearanceRoute.Appearance)
  }

  @Test
  @Config(qualifiers = "w360dp-h640dp")
  fun `a phone stays on the settings list`() {
    setContent(defaultDetailRoute = AppSettingsRoute.ChatsRoute.Chats)

    assertThat(router.navigator.detail.value).isNull()
  }

  @Test
  @Config(qualifiers = "w800dp-h700dp")
  fun `a split-pane window leaves settings rather than emptying the detail pane`() {
    setContent(defaultDetailRoute = AppSettingsRoute.ChatsRoute.Chats)

    router.onEvent(AppSettingsNavigationEvent.NavigateBack)
    composeTestRule.waitForIdle()

    assertThat(closed).isEqualTo(1)
    assertThat(router.navigator.detail.value).isEqualTo(AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  @Config(qualifiers = "w800dp-h700dp")
  fun `a split-pane window pops stacked detail`() {
    setContent(defaultDetailRoute = AppSettingsRoute.ChatsRoute.Chats)
    router.navigator.processEvent(ListDetailEvents.Push(AppSettingsRoute.ChatsRoute.Reactions))
    composeTestRule.waitForIdle()

    router.onEvent(AppSettingsNavigationEvent.NavigateBack)
    composeTestRule.waitForIdle()

    assertThat(closed).isEqualTo(0)
    assertThat(router.navigator.detail.value).isEqualTo(AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  @Config(qualifiers = "w360dp-h640dp")
  fun `a phone pops its only detail`() {
    router.navigator.processEvent(ListDetailEvents.Push(AppSettingsRoute.ChatsRoute.Chats))
    setContent(defaultDetailRoute = AppSettingsRoute.ChatsRoute.Chats)

    router.onEvent(AppSettingsNavigationEvent.NavigateBack)
    composeTestRule.waitForIdle()

    assertThat(closed).isEqualTo(0)
    assertThat(router.navigator.detail.value).isNull()
  }

  private fun setContent(defaultDetailRoute: AppSettingsRoute) {
    composeTestRule.setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        AppSettingsNavigation(
          router = router,
          onClose = { closed++ },
          home = { Text("Home") },
          defaultDetailRoute = defaultDetailRoute,
          detailEntries = {
            detailEntry<AppSettingsRoute.ChatsRoute.Chats> { Text("Chats") }
            detailEntry<AppSettingsRoute.AppearanceRoute.Appearance> { Text("Appearance") }
            detailEntry<AppSettingsRoute.ChatsRoute.Reactions> { Text("Reactions") }
          }
        )
      }
    }
    composeTestRule.waitForIdle()
  }
}
