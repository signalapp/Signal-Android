/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.text

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

@OptIn(ExperimentalCoroutinesApi::class)
class TextAvatarCreationViewModelTest {

  private val initialAvatar = Avatar.Text("AB", Avatars.colors[0], Avatar.DatabaseId.NotSet)

  private lateinit var viewModel: TextAvatarCreationViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    viewModel = TextAvatarCreationViewModel(initialAvatar)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state edits the avatar's text`() {
    assertThat(viewModel.state.value).isEqualTo(TextAvatarCreationState(initialAvatar, TextAvatarCreationState.Tab.TEXT))
  }

  @Test
  fun `TextChanged updates the avatar's text`() {
    viewModel.onEvent(TextAvatarCreationEvent.TextChanged("XY"))

    assertThat(viewModel.state.value.currentAvatar.text).isEqualTo("XY")
  }

  @Test
  fun `ColorSelected recolors the avatar`() {
    viewModel.onEvent(TextAvatarCreationEvent.ColorSelected(Avatars.colors[4]))

    assertThat(viewModel.state.value.currentAvatar.color).isEqualTo(Avatars.colors[4])
  }

  @Test
  fun `TabClicked switches tabs`() {
    viewModel.onEvent(TextAvatarCreationEvent.TabClicked(TextAvatarCreationState.Tab.COLOR))
    assertThat(viewModel.state.value.selectedTab).isEqualTo(TextAvatarCreationState.Tab.COLOR)

    viewModel.onEvent(TextAvatarCreationEvent.TabClicked(TextAvatarCreationState.Tab.TEXT))
    assertThat(viewModel.state.value.selectedTab).isEqualTo(TextAvatarCreationState.Tab.TEXT)
  }

  @Test
  fun `KeyboardNextClicked moves on to choosing a color`() {
    viewModel.onEvent(TextAvatarCreationEvent.KeyboardNextClicked)

    assertThat(viewModel.state.value.selectedTab).isEqualTo(TextAvatarCreationState.Tab.COLOR)
  }

  @Test
  fun `DoneClicked finishes with the edited avatar`() = runTest {
    val actions = mutableListOf<TextAvatarCreationAction>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.actions.toList(actions) }

    viewModel.onEvent(TextAvatarCreationEvent.TextChanged("XY"))
    viewModel.onEvent(TextAvatarCreationEvent.ColorSelected(Avatars.colors[4]))
    viewModel.onEvent(TextAvatarCreationEvent.DoneClicked)

    assertThat(actions).containsExactly(TextAvatarCreationAction.Finish(initialAvatar.copy(text = "XY", color = Avatars.colors[4])))
  }

  @Test
  fun `NavigateBackClicked closes without an avatar`() = runTest {
    val actions = mutableListOf<TextAvatarCreationAction>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.actions.toList(actions) }

    viewModel.onEvent(TextAvatarCreationEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(TextAvatarCreationAction.Close)
  }

  @Test
  fun `only the first way out of the editor is acted on`() = runTest {
    val actions = mutableListOf<TextAvatarCreationAction>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.actions.toList(actions) }

    viewModel.onEvent(TextAvatarCreationEvent.DoneClicked)
    viewModel.onEvent(TextAvatarCreationEvent.DoneClicked)
    viewModel.onEvent(TextAvatarCreationEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(TextAvatarCreationAction.Finish(initialAvatar))
  }
}
