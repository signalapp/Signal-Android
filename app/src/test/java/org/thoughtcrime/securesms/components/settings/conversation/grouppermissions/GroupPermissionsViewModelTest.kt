/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.grouppermissions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
import org.signal.chatsettings.screens.grouppermissions.GroupPermissions
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsEvents
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsState
import org.signal.chatsettings.screens.grouppermissions.GroupPermissionsState.Dialog
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult
import org.thoughtcrime.securesms.groups.ui.GroupErrors

@OptIn(ExperimentalCoroutinesApi::class)
class GroupPermissionsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()

  private val groupId = mockk<GroupId.V2>()
  private val repository = mockk<GroupPermissionsRepository>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.applyMembershipRightsChange(any(), any()) } returns GroupChangeResult.SUCCESS
    coEvery { repository.applyMemberLabelRightsChange(any(), any()) } returns GroupChangeResult.SUCCESS
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the group's permissions drive the state`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nonAdminsHaveMemberLabels = false)

    assertThat(viewModel.state.value).isEqualTo(
      GroupPermissionsState(
        permissions = GroupPermissions(
          selfCanEditSettings = true,
          nonAdminCanAddMembers = true,
          nonAdminCanEditGroupInfo = true,
          nonAdminCanSendMessages = true,
          nonAdminCanSetMemberLabel = true,
          nonAdminsHaveMemberLabels = false
        )
      )
    )
  }

  @Test
  fun `restricting member labels to admins applies immediately when no non-admin has a label`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nonAdminsHaveMemberLabels = false)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(false))
    advanceUntilIdle()

    coVerify { repository.applyMemberLabelRightsChange(groupId, GroupAccessControl.ONLY_ADMINS) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `restricting member labels to admins warns first when a non-admin has a label`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nonAdminsHaveMemberLabels = true)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(false))
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MemberLabelsWillBeCleared)
    coVerify(exactly = 0) { repository.applyMemberLabelRightsChange(any(), any()) }
  }

  @Test
  fun `opening member labels up to all members never warns`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nonAdminsHaveMemberLabels = true)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(true))
    advanceUntilIdle()

    coVerify { repository.applyMemberLabelRightsChange(groupId, GroupAccessControl.ALL_MEMBERS) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `confirming the warning restricts member labels to admins`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nonAdminsHaveMemberLabels = true)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(false))
    viewModel.onEvent(GroupPermissionsEvents.MemberLabelsWillBeClearedConfirmed)
    advanceUntilIdle()

    coVerify { repository.applyMemberLabelRightsChange(groupId, GroupAccessControl.ONLY_ADMINS) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `dismissing the warning leaves the permission alone`() = runTest(testDispatcher) {
    val viewModel = createViewModel(nonAdminsHaveMemberLabels = true)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(false))
    viewModel.onEvent(GroupPermissionsEvents.DialogDismissed)
    advanceUntilIdle()

    coVerify(exactly = 0) { repository.applyMemberLabelRightsChange(any(), any()) }
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `a change still in flight does not hold up the next event`() = runTest(testDispatcher) {
    val inFlight = CompletableDeferred<GroupChangeResult>()
    coEvery { repository.applyMembershipRightsChange(any(), any()) } coAnswers { inFlight.await() }

    val viewModel = createViewModel(nonAdminsHaveMemberLabels = true)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanAddMembers(true))
    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanSetMemberLabel(false))
    advanceUntilIdle()

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.MemberLabelsWillBeCleared)

    inFlight.complete(GroupChangeResult.SUCCESS)
    advanceUntilIdle()
  }

  @Test
  fun `a rejected change surfaces its failure reason`() = runTest(testDispatcher) {
    coEvery { repository.applyMembershipRightsChange(any(), any()) } returns GroupChangeResult.failure(GroupChangeFailureReason.NO_RIGHTS)

    val viewModel = createViewModel(nonAdminsHaveMemberLabels = false)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanAddMembers(false))
    advanceUntilIdle()

    assertThat(viewModel.state.value.errorMessage).isEqualTo(GroupErrors.getUserDisplayMessage(GroupChangeFailureReason.NO_RIGHTS))
  }

  @Test
  fun `a failure reason is cleared once its snackbar has been seen`() = runTest(testDispatcher) {
    coEvery { repository.applyMembershipRightsChange(any(), any()) } returns GroupChangeResult.failure(GroupChangeFailureReason.NO_RIGHTS)

    val viewModel = createViewModel(nonAdminsHaveMemberLabels = false)

    viewModel.onEvent(GroupPermissionsEvents.SetNonAdminCanAddMembers(false))
    advanceUntilIdle()
    viewModel.onEvent(GroupPermissionsEvents.SnackbarDismissed)
    advanceUntilIdle()

    assertThat(viewModel.state.value.errorMessage).isNull()
  }

  /**
   * Builds a view model and lets the group's permissions land before returning it, since the screen keeps every row
   * disabled until they do.
   */
  private fun TestScope.createViewModel(nonAdminsHaveMemberLabels: Boolean): GroupPermissionsViewModel {
    every { repository.observePermissions(groupId) } returns flowOf(
      GroupPermissions(
        selfCanEditSettings = true,
        nonAdminCanAddMembers = true,
        nonAdminCanEditGroupInfo = true,
        nonAdminCanSendMessages = true,
        nonAdminCanSetMemberLabel = true,
        nonAdminsHaveMemberLabels = nonAdminsHaveMemberLabels
      )
    )

    return GroupPermissionsViewModel(
      groupId = groupId,
      repository = repository
    ).also { advanceUntilIdle() }
  }
}
