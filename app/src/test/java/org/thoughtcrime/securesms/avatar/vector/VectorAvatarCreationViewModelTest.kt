/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

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
class VectorAvatarCreationViewModelTest {

  private val initialAvatar = Avatar.Vector("avatar_abstract_01", Avatars.colors[0], Avatar.DatabaseId.NotSet)

  private lateinit var viewModel: VectorAvatarCreationViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    viewModel = VectorAvatarCreationViewModel(initialAvatar)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is the avatar being edited`() {
    assertThat(viewModel.state.value.currentAvatar).isEqualTo(initialAvatar)
  }

  @Test
  fun `ColorSelected recolors the avatar`() {
    viewModel.onEvent(VectorAvatarCreationEvent.ColorSelected(Avatars.colors[4]))

    assertThat(viewModel.state.value.currentAvatar).isEqualTo(initialAvatar.copy(color = Avatars.colors[4]))
  }

  @Test
  fun `DoneClicked finishes with the recolored avatar`() = runTest {
    val actions = mutableListOf<VectorAvatarCreationAction>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.actions.toList(actions) }

    viewModel.onEvent(VectorAvatarCreationEvent.ColorSelected(Avatars.colors[4]))
    viewModel.onEvent(VectorAvatarCreationEvent.DoneClicked)

    assertThat(actions).containsExactly(VectorAvatarCreationAction.Finish(initialAvatar.copy(color = Avatars.colors[4])))
  }

  @Test
  fun `NavigateBackClicked closes without an avatar`() = runTest {
    val actions = mutableListOf<VectorAvatarCreationAction>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.actions.toList(actions) }

    viewModel.onEvent(VectorAvatarCreationEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(VectorAvatarCreationAction.Close)
  }

  @Test
  fun `only the first way out of the editor is acted on`() = runTest {
    val actions = mutableListOf<VectorAvatarCreationAction>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.actions.toList(actions) }

    viewModel.onEvent(VectorAvatarCreationEvent.DoneClicked)
    viewModel.onEvent(VectorAvatarCreationEvent.DoneClicked)
    viewModel.onEvent(VectorAvatarCreationEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(VectorAvatarCreationAction.Finish(initialAvatar))
  }
}
