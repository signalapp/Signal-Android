/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.editprofile.EditProfileAction
import org.signal.appsettings.editprofile.EditProfileEvent
import org.signal.appsettings.editprofile.EditProfileState
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.models.badges.Badge
import org.signal.core.models.media.Media
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameDeleteResult
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val navigationEvents = mutableListOf<AppSettingsNavigationEvent>()
  private val self = MutableStateFlow(recipient(name = "Alex Hart", about = "Speak freely", aboutEmoji = "👋"))
  private val sustainerBadge = mockk<Badge>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(EditProfileRepository)

    every { EditProfileRepository.observeSelf() } returns self
    every { EditProfileRepository.getUsername() } returns null
    every { EditProfileRepository.getUsernameSyncState() } returns EditProfileState.UsernameSyncState.IN_SYNC
    every { EditProfileRepository.canEdit() } returns true
    every { EditProfileRepository.isClientDeprecated() } returns false
    every { EditProfileRepository.shouldShowUsernameLinkTooltip() } returns false
    every { EditProfileRepository.markUsernameLinkTooltipSeen() } just Runs
    every { EditProfileRepository.hasBadges() } returns false
    every { EditProfileRepository.refreshOwnProfile() } just Runs
    coEvery { EditProfileRepository.getSustainerBadge() } returns sustainerBadge
    coEvery { EditProfileRepository.deleteUsername() } returns UsernameDeleteResult.SUCCESS
    coEvery { EditProfileRepository.readAvatar(any()) } returns byteArrayOf(1, 2, 3)
    coEvery { EditProfileRepository.setAvatar(any(), any()) } returns EditProfileRepository.SaveResult.SUCCESS
    coEvery { EditProfileRepository.clearAvatar() } returns EditProfileRepository.SaveResult.SUCCESS
  }

  @After
  fun tearDown() {
    unmockkObject(EditProfileRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the profile is read from the self recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value.profileName).isEqualTo("Alex Hart")
    assertThat(viewModel.state.value.about).isEqualTo("Speak freely")
    assertThat(viewModel.state.value.aboutEmoji).isEqualTo("👋")
    verify { EditProfileRepository.refreshOwnProfile() }
  }

  @Test
  fun `a change to the self recipient updates the profile`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    self.value = recipient(name = "Sam Smith", about = "", aboutEmoji = "")

    assertThat(viewModel.state.value.profileName).isEqualTo("Sam Smith")
    assertThat(viewModel.state.value.about).isEqualTo("")
  }

  @Test
  fun `each row opens the screen that edits it`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.NameClicked)
    viewModel.onEvent(EditProfileEvent.AboutClicked)
    viewModel.onEvent(EditProfileEvent.EditPhotoClicked)
    viewModel.onEvent(EditProfileEvent.UsernameClicked)

    assertThat(navigationEvents).containsExactly(
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AccountRoute.ProfileName),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AccountRoute.About),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AccountRoute.AvatarPicker),
      AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AccountRoute.Username())
    )
  }

  @Test
  fun `an unregistered device explains why instead of opening anything`() = runTest(testDispatcher) {
    every { EditProfileRepository.canEdit() } returns false
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.NameClicked)

    assertThat(viewModel.state.value.canEdit).isFalse()
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.Unregistered)
    assertThat(navigationEvents).isEmpty()
  }

  @Test
  fun `an expired app explains that it needs updating`() = runTest(testDispatcher) {
    every { EditProfileRepository.canEdit() } returns false
    every { EditProfileRepository.isClientDeprecated() } returns true
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditProfileEvent.AboutClicked)
    viewModel.onEvent(EditProfileEvent.UpdateAppClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.None)
    assertThat(actions).containsExactly(EditProfileAction.OpenAppUpdate)
  }

  @Test
  fun `ReregisterClicked starts registration over`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditProfileEvent.ReregisterClicked)

    assertThat(actions).containsExactly(EditProfileAction.StartReregistration)
  }

  @Test
  fun `AvatarClicked opens the avatar full screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditProfileEvent.AvatarClicked)

    assertThat(actions).containsExactly(EditProfileAction.OpenAvatarPreview)
  }

  @Test
  fun `badges opens badge management for a user who has badges`() = runTest(testDispatcher) {
    every { EditProfileRepository.hasBadges() } returns true
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.BadgesClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.DonationsRoute.Badges))
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.None)
  }

  @Test
  fun `badges invites a user without badges to become a Sustainer`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.BadgesClicked)

    assertThat(navigationEvents).isEmpty()
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.BecomeASustainer)
    assertThat(viewModel.state.value.sustainerBadge).isEqualTo(sustainerBadge)
  }

  @Test
  fun `BecomeASustainerClicked closes the sheet and opens subscriptions`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditProfileEvent.BadgesClicked)
    viewModel.onEvent(EditProfileEvent.BecomeASustainerClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.None)
    assertThat(actions).containsExactly(EditProfileAction.OpenSubscriptions)
  }

  @Test
  fun `the username row offers to edit or delete an existing username`() = runTest(testDispatcher) {
    every { EditProfileRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.UsernameClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.UsernameOptions)

    viewModel.onEvent(EditProfileEvent.EditUsernameClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.None)
    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.AccountRoute.Username()))
  }

  @Test
  fun `deleting the username confirms first, then deletes it`() = runTest(testDispatcher) {
    every { EditProfileRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.DeleteUsernameClicked)
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.ConfirmDeleteUsername("spider.42"))

    every { EditProfileRepository.getUsername() } returns null
    viewModel.onEvent(EditProfileEvent.DeleteUsernameConfirmed)

    coVerify { EditProfileRepository.deleteUsername() }
    assertThat(viewModel.state.value.dialog).isEqualTo(EditProfileState.Dialog.None)
    assertThat(viewModel.state.value.busy).isFalse()
    assertThat(viewModel.state.value.username).isNull()
    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.UsernameDeleted)
  }

  @Test
  fun `a failed delete says so`() = runTest(testDispatcher) {
    every { EditProfileRepository.getUsername() } returns "spider.42"
    coEvery { EditProfileRepository.deleteUsername() } returns UsernameDeleteResult.NETWORK_ERROR
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.DeleteUsernameClicked)
    viewModel.onEvent(EditProfileEvent.DeleteUsernameConfirmed)

    assertThat(viewModel.state.value.username).isEqualTo("spider.42")
    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.UsernameDeleteFailed)
  }

  @Test
  fun `resuming with a new username says it was created`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(EditProfileEvent.ScreenResumed)

    every { EditProfileRepository.getUsername() } returns "spider.42"
    viewModel.onEvent(EditProfileEvent.ScreenResumed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.UsernameCreated)
  }

  @Test
  fun `resuming with a changed username also says it was created`() = runTest(testDispatcher) {
    every { EditProfileRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()
    viewModel.onEvent(EditProfileEvent.ScreenResumed)

    every { EditProfileRepository.getUsername() } returns "venom.42"
    viewModel.onEvent(EditProfileEvent.ScreenResumed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.UsernameCreated)
  }

  @Test
  fun `resuming with the same username says nothing`() = runTest(testDispatcher) {
    every { EditProfileRepository.getUsername() } returns "spider.42"
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.ScreenResumed)
    viewModel.onEvent(EditProfileEvent.ScreenResumed)

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.None)
  }

  @Test
  fun `a picked avatar is saved`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.AvatarSelected(media(contentType = "image/png")))

    coVerify { EditProfileRepository.setAvatar(byteArrayOf(1, 2, 3), "image/png") }
    assertThat(viewModel.state.value.busy).isFalse()
    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.None)
  }

  @Test
  fun `an avatar that can't be read says so without saving`() = runTest(testDispatcher) {
    coEvery { EditProfileRepository.readAvatar(any()) } returns null
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.AvatarSelected(media(contentType = "image/png")))

    coVerify(exactly = 0) { EditProfileRepository.setAvatar(any(), any()) }
    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.AvatarReadFailed)
  }

  @Test
  fun `an avatar that can't be uploaded says so`() = runTest(testDispatcher) {
    coEvery { EditProfileRepository.setAvatar(any(), any()) } returns EditProfileRepository.SaveResult.NETWORK_FAILURE
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.AvatarSelected(media(contentType = "image/png")))

    assertThat(viewModel.state.value.snackbar).isEqualTo(EditProfileState.Snackbar.AvatarNetworkFailure)
  }

  @Test
  fun `AvatarCleared removes the avatar`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.AvatarCleared)

    coVerify { EditProfileRepository.clearAvatar() }
    assertThat(viewModel.state.value.busy).isFalse()
  }

  @Test
  fun `the username link tooltip goes away for good once dismissed`() = runTest(testDispatcher) {
    every { EditProfileRepository.shouldShowUsernameLinkTooltip() } returns true
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.UsernameLinkTooltipDismissed)

    verify { EditProfileRepository.markUsernameLinkTooltipSeen() }
    assertThat(viewModel.state.value.showUsernameLinkTooltip).isFalse()
  }

  @Test
  fun `UsernameLinkClicked opens the username link`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.UsernameLinkClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateToScreen(AppSettingsRoute.UsernameLinkRoute.UsernameLink))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditProfileEvent.NavigateBackClicked)

    assertThat(navigationEvents).containsExactly(AppSettingsNavigationEvent.NavigateBack)
  }

  private fun createViewModel(): EditProfileViewModel = EditProfileViewModel(parentEventEmitter = { navigationEvents += it })

  private fun recipient(name: String, about: String, aboutEmoji: String): Recipient {
    return mockk {
      every { profileName } returns ProfileName.fromParts(name.substringBefore(" "), name.substringAfter(" ", ""))
      every { this@mockk.about } returns about
      every { this@mockk.aboutEmoji } returns aboutEmoji
    }
  }

  private fun media(contentType: String): Media {
    return mockk {
      every { this@mockk.contentType } returns contentType
    }
  }

  private fun TestScope.collectActions(actions: Flow<EditProfileAction>): List<EditProfileAction> {
    val collected = mutableListOf<EditProfileAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
