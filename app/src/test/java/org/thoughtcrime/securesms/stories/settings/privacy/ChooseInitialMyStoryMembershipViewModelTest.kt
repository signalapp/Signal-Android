/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.privacy

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.stories.settings.my.MyStoryPrivacyState
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ChooseInitialMyStoryMembershipViewModelTest {

  companion object {
    private val RECIPIENT_ID = RecipientId.from(1)

    private val REPOSITORY_STATE = ChooseInitialMyStoryMembershipState(
      recipientId = RECIPIENT_ID,
      privacyState = MyStoryPrivacyState(privacyMode = DistributionListPrivacyMode.ALL_EXCEPT, connectionCount = 3),
      allSignalConnectionsCount = 42
    )
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private val repositoryStates = MutableSharedFlow<ChooseInitialMyStoryMembershipState>()
  private val repository = mockk<MyStorySettingsRepository>(relaxUnitFun = true) {
    every { observeChooseInitialPrivacy() } returns repositoryStates
  }

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is empty until the repository emits`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(ChooseInitialMyStoryMembershipState())
  }

  @Test
  fun `repository emissions update the state`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    repositoryStates.emit(REPOSITORY_STATE)

    assertThat(viewModel.state.value).isEqualTo(REPOSITORY_STATE)
    assertThat(viewModel.state.value.hasUserPerformedManualSelection).isFalse()
  }

  @Test
  fun `select writes the privacy mode and marks a manual selection`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.select(DistributionListPrivacyMode.ONLY_WITH)

    coVerify { repository.setMyStoryPrivacyMode(DistributionListPrivacyMode.ONLY_WITH) }
    assertThat(viewModel.state.value.hasUserPerformedManualSelection).isTrue()
  }

  @Test
  fun `repository emissions after select keep the manual selection`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.select(DistributionListPrivacyMode.ALL)
    repositoryStates.emit(REPOSITORY_STATE)

    assertThat(viewModel.state.value).isEqualTo(REPOSITORY_STATE.copy(hasUserPerformedManualSelection = true))
  }

  @Test
  fun `save persists the selection and returns the my story recipient`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    repositoryStates.emit(REPOSITORY_STATE)

    val recipientId = viewModel.save()

    assertThat(recipientId).isEqualTo(RECIPIENT_ID)
    coVerifyOrder {
      repository.markUserPerformedManualSelection()
      repository.notifyStorySettingsChanged()
    }
  }

  private fun createViewModel(): ChooseInitialMyStoryMembershipViewModel {
    return ChooseInitialMyStoryMembershipViewModel(repository)
  }
}
