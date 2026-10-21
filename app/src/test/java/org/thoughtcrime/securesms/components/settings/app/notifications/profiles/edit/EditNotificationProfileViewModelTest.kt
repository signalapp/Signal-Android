/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.edit.EditNotificationProfileRepository.SaveResult
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class EditNotificationProfileViewModelTest {

  companion object {
    private const val PROFILE_ID = 6L
    private const val CREATED_ID = 9L
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val profile = NotificationProfile(
    id = PROFILE_ID,
    name = "Work",
    emoji = "💪",
    createdAt = 0L,
    schedule = NotificationProfileSchedule(id = PROFILE_ID),
    notificationProfileId = NotificationProfileId(UUID(0L, PROFILE_ID))
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(EditNotificationProfileRepository)

    coEvery { EditNotificationProfileRepository.getProfile(PROFILE_ID) } returns profile
    coEvery { EditNotificationProfileRepository.createProfile(any(), any()) } returns SaveResult.Success(CREATED_ID)
    coEvery { EditNotificationProfileRepository.updateProfile(PROFILE_ID, any(), any()) } returns SaveResult.Success(PROFILE_ID)
  }

  @After
  fun tearDown() {
    unmockkObject(EditNotificationProfileRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `create mode starts empty and ready`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(EditNotificationProfileState(createMode = true, isLoaded = true))
    assertThat(viewModel.state.value.name).isEqualTo("")
  }

  @Test
  fun `edit mode reads the profile's name and emoji`() = runTest(testDispatcher) {
    val viewModel = createViewModel(PROFILE_ID)

    assertThat(viewModel.state.value).isEqualTo(EditNotificationProfileState(createMode = false, isLoaded = true, name = "Work", emoji = profile.emoji))
    assertThat(viewModel.state.value.name).isEqualTo("Work")
  }

  @Test
  fun `edit mode leaves when the profile no longer exists`() = runTest(testDispatcher) {
    val loaded = CompletableDeferred<NotificationProfile?>()
    coEvery { EditNotificationProfileRepository.getProfile(PROFILE_ID) } coAnswers { loaded.await() }

    val viewModel = createViewModel(PROFILE_ID)
    val actions = collectActions(viewModel.actions)
    loaded.complete(null)

    assertThat(viewModel.state.value.isLoaded).isFalse()
    assertThat(actions).containsExactly(EditNotificationProfileAction.NavigateBack)
  }

  @Test
  fun `NameChanged sets the name`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileEvent.NameChanged("Gym"))

    assertThat(viewModel.state.value.name).isEqualTo("Gym")
  }

  @Test
  fun `the name stops at 32 characters`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileEvent.NameChanged("a".repeat(30) + "bcde"))

    assertThat(viewModel.state.value.name).isEqualTo("a".repeat(30) + "bc")
  }

  @Test
  fun `EmojiButtonClicked opens the emoji picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileEvent.EmojiButtonClicked)

    assertThat(viewModel.state.value.showEmojiPicker).isTrue()
  }

  @Test
  fun `picking an emoji sets it and closes the picker`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileEvent.EmojiButtonClicked)
    viewModel.onEvent(EditNotificationProfileEvent.EmojiSelected("\uD83D\uDD25"))

    assertThat(viewModel.state.value.emoji).isEqualTo("\uD83D\uDD25")
    assertThat(viewModel.state.value.showEmojiPicker).isFalse()
  }

  @Test
  fun `dismissing the picker keeps the emoji`() = runTest(testDispatcher) {
    val viewModel = createViewModel(PROFILE_ID)

    viewModel.onEvent(EditNotificationProfileEvent.EmojiButtonClicked)
    viewModel.onEvent(EditNotificationProfileEvent.EmojiPickerDismissed)

    assertThat(viewModel.state.value.showEmojiPicker).isFalse()
    assertThat(viewModel.state.value.emoji).isEqualTo(profile.emoji)
  }

  @Test
  fun `ClearClicked clears the name and emoji`() = runTest(testDispatcher) {
    val viewModel = createViewModel(PROFILE_ID)

    viewModel.onEvent(EditNotificationProfileEvent.ClearClicked)

    assertThat(viewModel.state.value.name).isEqualTo("")
    assertThat(viewModel.state.value.emoji).isEqualTo("")
  }

  @Test
  fun `PresetClicked fills in the name and emoji`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileEvent.PresetClicked(name = "Sleep", emoji = "😴"))

    assertThat(viewModel.state.value.name).isEqualTo("Sleep")
    assertThat(viewModel.state.value.emoji).isEqualTo("😴")
  }

  @Test
  fun `saving without a name shows an error and saves nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)

    assertThat(viewModel.state.value.nameError).isEqualTo(EditNotificationProfileState.NameError.Empty)
    coVerify(exactly = 0) { EditNotificationProfileRepository.createProfile(any(), any()) }
  }

  @Test
  fun `editing the name clears the error`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)

    viewModel.onEvent(EditNotificationProfileEvent.NameChanged("W"))

    assertThat(viewModel.state.value.nameError).isEqualTo(EditNotificationProfileState.NameError.None)
  }

  @Test
  fun `creating a profile continues to its allowed members`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditNotificationProfileEvent.PresetClicked(name = "Sleep", emoji = "😴"))
    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)

    coVerify(exactly = 1) { EditNotificationProfileRepository.createProfile("Sleep", "😴") }
    assertThat(actions).containsExactly(EditNotificationProfileAction.NavigateToAllowedMembers(CREATED_ID))
  }

  @Test
  fun `saving an edit goes back`() = runTest(testDispatcher) {
    val viewModel = createViewModel(PROFILE_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditNotificationProfileEvent.EmojiSelected("🔥"))
    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)

    coVerify(exactly = 1) { EditNotificationProfileRepository.updateProfile(PROFILE_ID, "Work", "🔥") }
    assertThat(actions).containsExactly(EditNotificationProfileAction.NavigateBack)
  }

  @Test
  fun `a duplicate name shows an error and stays put`() = runTest(testDispatcher) {
    coEvery { EditNotificationProfileRepository.updateProfile(PROFILE_ID, any(), any()) } returns SaveResult.DuplicateName

    val viewModel = createViewModel(PROFILE_ID)
    val actions = collectActions(viewModel.actions)
    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)

    assertThat(viewModel.state.value.nameError).isEqualTo(EditNotificationProfileState.NameError.Duplicate)
    assertThat(viewModel.state.value.isSaving).isFalse()
    assertThat(actions).isEmpty()
  }

  @Test
  fun `saving shows progress until the save finishes`() = runTest(testDispatcher) {
    val saved = CompletableDeferred<SaveResult>()
    coEvery { EditNotificationProfileRepository.updateProfile(PROFILE_ID, any(), any()) } coAnswers { saved.await() }

    val viewModel = createViewModel(PROFILE_ID)
    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)
    assertThat(viewModel.state.value.isSaving).isTrue()

    saved.complete(SaveResult.Success(PROFILE_ID))
    assertThat(viewModel.state.value.isSaving).isFalse()
  }

  @Test
  fun `nothing is saved before an existing profile loads`() = runTest(testDispatcher) {
    val loaded = CompletableDeferred<NotificationProfile?>()
    coEvery { EditNotificationProfileRepository.getProfile(PROFILE_ID) } coAnswers { loaded.await() }

    val viewModel = createViewModel(PROFILE_ID)
    viewModel.onEvent(EditNotificationProfileEvent.SaveClicked)

    coVerify(exactly = 0) { EditNotificationProfileRepository.updateProfile(any(), any(), any()) }
    assertThat(viewModel.state.value.nameError).isEqualTo(EditNotificationProfileState.NameError.None)
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(EditNotificationProfileEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(EditNotificationProfileAction.NavigateBack)
  }

  private fun createViewModel(profileId: Long = EditNotificationProfileViewModel.NEW_PROFILE): EditNotificationProfileViewModel {
    return EditNotificationProfileViewModel(profileId)
  }

  private fun TestScope.collectActions(actions: Flow<EditNotificationProfileAction>): List<EditNotificationProfileAction> {
    val collected = mutableListOf<EditNotificationProfileAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
