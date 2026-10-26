/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.routes

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppSettingsRouterTest {

  private lateinit var router: AppSettingsRouter

  private val stack
    get() = router.navigator[AppSettingsHomeRoute]

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    router = AppSettingsRouter(SavedStateHandle())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `given a new router, then only home is on the stack`() {
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `when navigating to a section, then it is displayed beside home`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  fun `when navigating to another section, then it replaces the one displayed`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Reactions))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.Privacy))

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.PrivacyRoute.Privacy)
  }

  @Test
  fun `when navigating within a section, then it stacks on top`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Reactions))

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats, AppSettingsRoute.ChatsRoute.Reactions)
  }

  @Test
  fun `when navigating to empty, then the detail is cleared`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Reactions))

    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.Empty))

    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `when navigating back from a section, then it is popped`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))

    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack)

    assertThat(handled).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `when navigating back from home, then it is left to the host`() {
    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack)

    assertThat(handled).isFalse()
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `given nothing open, then no route is selected`() {
    assertThat(router.selectedRoute.value).isNull()
  }

  @Test
  fun `when navigating within a section, then the section stays selected`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Reactions))
    Snapshot.sendApplyNotifications()

    assertThat(router.selectedRoute.value).isEqualTo(AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  fun `when navigating back from a section, then nothing is selected`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    Snapshot.sendApplyNotifications()
    router.navigate(AppSettingsNavigationEvent.NavigateBack)
    Snapshot.sendApplyNotifications()

    assertThat(router.selectedRoute.value).isNull()
  }
}
