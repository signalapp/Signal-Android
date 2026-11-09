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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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

  private val closeDetails = PopUpTo(AppSettingsRoute.NotificationsRoute.ProfileDetails::class, inclusive = true)

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
  fun `when opening another section, then it replaces the screens displayed`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.PrivacyRoute.Privacy, replacesDetail = true))

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.PrivacyRoute.Privacy)
  }

  @Test
  fun `when navigating within a section, then it stacks on top`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats, AppSettingsRoute.ChatFoldersRoute.ChatFolders)
  }

  @Test
  fun `when navigating to empty, then the detail is cleared`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))

    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.Empty))

    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `when navigating back from a section, then it is popped`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))

    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack())

    assertThat(handled).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `when navigating back from home, then it is left to the host`() {
    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack())

    assertThat(handled).isFalse()
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `when navigating from within a section to another section's screen, then it stacks on top`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AccountRoute.ManageProfile))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.UsernameLinkRoute.UsernameLink))

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.AccountRoute.ManageProfile, AppSettingsRoute.UsernameLinkRoute.UsernameLink)
  }

  @Test
  fun `given a section opened directly, when navigating back from it, then it is left to the host`() {
    router.openDirectly(AppSettingsRoute.UsernameLinkRoute.UsernameLink)

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack())).isFalse()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.UsernameLinkRoute.UsernameLink)
  }

  @Test
  fun `given a section opened directly, when navigating back from a screen within it, then that screen is popped`() {
    router.openDirectly(AppSettingsRoute.UsernameLinkRoute.UsernameLink)
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.UsernameLinkRoute.QRColorPicker))

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack())).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.UsernameLinkRoute.UsernameLink)
  }

  @Test
  fun `given a section opened directly, when opening a section from home, then going back from it returns home`() {
    router.openDirectly(AppSettingsRoute.UsernameLinkRoute.UsernameLink)
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats, replacesDetail = true))

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack())).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `given split pane, when navigating back from a section, then it is left to the host`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack(), isSplitPane = true)).isFalse()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  fun `given split pane, when navigating back from a screen within a section, then that screen is popped`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack(), isSplitPane = true)).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  fun `when navigating with an inclusive pop up to, then that screen and those above it are replaced`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.NotificationProfiles))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.AddAllowedMembers(1)))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.EditSchedule(1, createMode = true)))

    router.navigate(
      AppSettingsNavigationEvent.NavigateToScreen(
        route = AppSettingsRoute.NotificationsRoute.Created(1),
        popUpTo = PopUpTo(AppSettingsRoute.NotificationsRoute.AddAllowedMembers::class, inclusive = true)
      )
    )

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.NotificationsRoute.NotificationProfiles, AppSettingsRoute.NotificationsRoute.Created(1))
  }

  @Test
  fun `when navigating with an exclusive pop up to, then only the screens above it are replaced`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))

    router.navigate(
      AppSettingsNavigationEvent.NavigateToScreen(
        route = AppSettingsRoute.ChatFoldersRoute.ChooseChats(includeChats = true),
        popUpTo = PopUpTo(AppSettingsRoute.ChatsRoute.Chats::class)
      )
    )

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats, AppSettingsRoute.ChatFoldersRoute.ChooseChats(includeChats = true))
  }

  @Test
  fun `when navigating to a screen already open with a pop up to it, then it is returned to`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChooseChats(includeChats = true)))

    router.navigate(
      AppSettingsNavigationEvent.NavigateToScreen(
        route = AppSettingsRoute.ChatsRoute.Chats,
        popUpTo = PopUpTo(AppSettingsRoute.ChatsRoute.Chats::class)
      )
    )

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  fun `when navigating with a pop up to a screen that is not open, then the screen is pushed`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))

    router.navigate(
      AppSettingsNavigationEvent.NavigateToScreen(
        route = AppSettingsRoute.ChatFoldersRoute.ChatFolders,
        popUpTo = PopUpTo(AppSettingsRoute.PrivacyRoute.Privacy::class, inclusive = true)
      )
    )

    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.ChatsRoute.Chats, AppSettingsRoute.ChatFoldersRoute.ChatFolders)
  }

  @Test
  fun `when navigating back with a pop up to, then that screen and those above it are closed`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.NotificationProfiles))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.ProfileDetails(1)))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.EditProfile(1)))

    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack(popUpTo = closeDetails))

    assertThat(handled).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.NotificationsRoute.NotificationProfiles)
  }

  @Test
  fun `when navigating back with a pop up to a screen that is not open, then nothing is closed`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.NotificationProfiles))

    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack(popUpTo = closeDetails))

    assertThat(handled).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.NotificationsRoute.NotificationProfiles)
  }

  @Test
  fun `when navigating back with a pop up to that closes every screen, then home is left on its own`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.ProfileDetails(1)))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.EditProfile(1)))

    val handled = router.navigate(AppSettingsNavigationEvent.NavigateBack(popUpTo = closeDetails))

    assertThat(handled).isTrue()
    assertThat(stack).containsExactly(AppSettingsHomeRoute)
  }

  @Test
  fun `given a screen opened directly, when navigating back with a pop up to it, then it is left to the host`() {
    router.openDirectly(AppSettingsRoute.NotificationsRoute.ProfileDetails(1))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.EditProfile(1)))

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack(popUpTo = closeDetails))).isFalse()
    assertThat(stack).containsExactly(AppSettingsHomeRoute, AppSettingsRoute.NotificationsRoute.ProfileDetails(1), AppSettingsRoute.NotificationsRoute.EditProfile(1))
  }

  @Test
  fun `given split pane, when navigating back with a pop up to the first screen beside the list, then it is left to the host`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.ProfileDetails(1)))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.NotificationsRoute.EditProfile(1)))

    assertThat(router.navigate(AppSettingsNavigationEvent.NavigateBack(popUpTo = closeDetails), isSplitPane = true)).isFalse()
  }

  @Test
  fun `when an event is sent before anything collects, then it is delivered once collected`() = runTest {
    router.onEvent(AppSettingsNavigationEvent.NavigateBack())

    assertThat(router.events.first()).isEqualTo(AppSettingsNavigationEvent.NavigateBack())
  }

  @Test
  fun `given nothing open, then no route is selected`() {
    assertThat(router.selectedRoute.value).isNull()
  }

  @Test
  fun `when navigating within a section, then the section stays selected`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatFoldersRoute.ChatFolders))
    Snapshot.sendApplyNotifications()

    assertThat(router.selectedRoute.value).isEqualTo(AppSettingsRoute.ChatsRoute.Chats)
  }

  @Test
  fun `when navigating back from a section, then nothing is selected`() {
    router.navigate(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.ChatsRoute.Chats))
    Snapshot.sendApplyNotifications()
    router.navigate(AppSettingsNavigationEvent.NavigateBack())
    Snapshot.sendApplyNotifications()

    assertThat(router.selectedRoute.value).isNull()
  }
}
