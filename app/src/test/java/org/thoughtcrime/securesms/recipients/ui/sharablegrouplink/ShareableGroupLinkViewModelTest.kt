/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.sharablegrouplink

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.chatsettings.screens.sharablegrouplink.GroupLink
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkEvents
import org.signal.chatsettings.screens.sharablegrouplink.ShareableGroupLinkState.Dialog
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.ui.GroupErrors

@OptIn(ExperimentalCoroutinesApi::class)
class ShareableGroupLinkViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val groupId = mockk<GroupId.V2>()
  private val repository = mockk<ShareableGroupLinkRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.setGroupLinkState(any(), any(), any()) } returns GroupChangeResult.SUCCESS
    coEvery { repository.cycleGroupLinkPassword(any()) } returns GroupChangeResult.SUCCESS
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the group's link drives the state`() = runTest(testDispatcher) {
    val groupLink = GroupLink(enabled = true, requiresAdminApproval = true, url = GROUP_LINK_URL, selfCanEditSettings = true)

    val viewModel = createViewModel(groupLink)

    assertThat(viewModel.state.value.groupLink).isEqualTo(groupLink)
  }

  @Test
  fun `turning the link on leaves the approval requirement alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = false, requiresAdminApproval = true, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.GroupLinkToggled)
    advanceUntilIdle()

    coVerify { repository.setGroupLinkState(groupId, enabled = true, requiresAdminApproval = true) }
  }

  @Test
  fun `turning the link off leaves the approval requirement alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = true, requiresAdminApproval = true, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.GroupLinkToggled)
    advanceUntilIdle()

    coVerify { repository.setGroupLinkState(groupId, enabled = false, requiresAdminApproval = true) }
  }

  @Test
  fun `requiring admin approval leaves the link on`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = true, requiresAdminApproval = false, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.AdminApprovalToggled)
    advanceUntilIdle()

    coVerify { repository.setGroupLinkState(groupId, enabled = true, requiresAdminApproval = true) }
  }

  @Test
  fun `no longer requiring admin approval leaves the link on`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = true, requiresAdminApproval = true, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.AdminApprovalToggled)
    advanceUntilIdle()

    coVerify { repository.setGroupLinkState(groupId, enabled = true, requiresAdminApproval = false) }
  }

  @Test
  fun `resetting the link warns first`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = true, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.ResetLinkClicked)
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmResetLink)
    coVerify(exactly = 0) { repository.cycleGroupLinkPassword(any()) }
  }

  @Test
  fun `confirming the warning gives the group a new link`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = true, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.ResetLinkClicked)
    viewModel.onEvent(ShareableGroupLinkEvents.ResetLinkConfirmed)
    advanceUntilIdle()

    coVerify { repository.cycleGroupLinkPassword(groupId) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `dismissing the warning leaves the link alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(enabled = true, selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.ResetLinkClicked)
    viewModel.onEvent(ShareableGroupLinkEvents.DialogDismissed)
    advanceUntilIdle()

    coVerify(exactly = 0) { repository.cycleGroupLinkPassword(any()) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `a change in flight is reported as busy`() = runTest(testDispatcher) {
    val inFlight = CompletableDeferred<GroupChangeResult>()
    coEvery { repository.setGroupLinkState(any(), any(), any()) } coAnswers { inFlight.await() }

    val viewModel = createViewModel(GroupLink.NONE.copy(selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.GroupLinkToggled)
    advanceUntilIdle()

    assertThat(viewModel.state.value.busy).isTrue()

    inFlight.complete(GroupChangeResult.SUCCESS)
    advanceUntilIdle()

    assertThat(viewModel.state.value.busy).isFalse()
  }

  @Test
  fun `a rejected change surfaces its failure reason`() = runTest(testDispatcher) {
    coEvery { repository.setGroupLinkState(any(), any(), any()) } returns GroupChangeResult.failure(GroupChangeFailureReason.NO_RIGHTS)

    val viewModel = createViewModel(GroupLink.NONE.copy(selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.GroupLinkToggled)
    advanceUntilIdle()

    assertThat(viewModel.state.value.errorMessage).isEqualTo(GroupErrors.getUserDisplayMessage(GroupChangeFailureReason.NO_RIGHTS))
    assertThat(viewModel.state.value.busy).isFalse()
  }

  @Test
  fun `a failure reason is cleared once its snackbar has been seen`() = runTest(testDispatcher) {
    coEvery { repository.setGroupLinkState(any(), any(), any()) } returns GroupChangeResult.failure(GroupChangeFailureReason.NO_RIGHTS)

    val viewModel = createViewModel(GroupLink.NONE.copy(selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.GroupLinkToggled)
    advanceUntilIdle()
    viewModel.onEvent(ShareableGroupLinkEvents.SnackbarDismissed)
    advanceUntilIdle()

    assertThat(viewModel.state.value.errorMessage).isNull()
  }

  @Test
  fun `a change that goes through reports nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel(GroupLink.NONE.copy(selfCanEditSettings = true))

    viewModel.onEvent(ShareableGroupLinkEvents.GroupLinkToggled)
    advanceUntilIdle()

    assertThat(viewModel.state.value.errorMessage).isNull()
  }

  /**
   * Builds a view model and lets the group's link land before returning it, since every row reads off of it.
   */
  private fun TestScope.createViewModel(groupLink: GroupLink): ShareableGroupLinkViewModel {
    every { repository.observeGroupLink(groupId) } returns flowOf(groupLink)

    return ShareableGroupLinkViewModel(
      groupId = groupId,
      repository = repository
    ).also { advanceUntilIdle() }
  }

  companion object {
    private const val GROUP_LINK_URL = "https://signal.group/#CjQKIP_ZZ3Zz"
  }
}
