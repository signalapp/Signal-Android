/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class MemberLabelViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val memberLabelRepo = mockk<MemberLabelRepository>(relaxUnitFun = true)
  private val groupId = mockk<GroupId.V2>()
  private val recipientId = RecipientId.from(1L)

  @Test
  fun `isSaveEnabled returns true when label text is different from the original value`() {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("Modified")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when label text is the same as the original value`() {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("Original")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when label text is valid and the emoji is different from the original value`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelEmojiChanged("🎉")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when the label and emoji are not changed`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = "🎉", text = "Label")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when the label and emoji are changed to the original value`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)

    viewModel.onLabelEmojiChanged("🫢")
    viewModel.onLabelTextChanged("Modified")

    viewModel.onLabelEmojiChanged("🎉")
    viewModel.onLabelTextChanged("Original")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when label is too short`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("")
    viewModel.onLabelEmojiChanged("🎉")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when clearLabel is called with existing label and emoji`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.clearLabel()

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when clearLabel is called with existing label without emoji`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.clearLabel()

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when clearLabel is called with no existing label`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns null

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.clearLabel()

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when both emoji and label are modified`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("New Label")
    viewModel.onLabelEmojiChanged("🚀")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when only emoji is changed without an existing label`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns null

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelEmojiChanged("🎉")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `save does not call setLabel when isSaveEnabled is false`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.save()

    coVerify(exactly = 0) { memberLabelRepo.setLabel(groupId, any()) }
  }

  @Test
  fun `save does not call setLabel when label is less than 1 character`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("")
    viewModel.onLabelEmojiChanged("🎉")
    viewModel.save()

    coVerify(exactly = 0) { memberLabelRepo.setLabel(groupId, any()) }
  }

  @Test
  fun `save calls setLabel with truncated label when label exceeds max length`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns null

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("A".repeat(30))
    viewModel.save()

    coVerify(exactly = 1) {
      memberLabelRepo.setLabel(
        groupId = groupId,
        label = match { it.text.length == 24 }
      )
    }
  }

  @Test
  fun `save does not call setLabel when emoji is set with no label`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns null

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelEmojiChanged("🎉")
    viewModel.save()

    coVerify(exactly = 0) { memberLabelRepo.setLabel(groupId, any()) }
  }

  @Test
  fun `save calls setLabel when label change is valid`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.onLabelTextChanged("New Label")
    viewModel.onLabelEmojiChanged("🎉")
    viewModel.save()

    coVerify(exactly = 1) {
      memberLabelRepo.setLabel(groupId, MemberLabel(text = "New Label", emoji = "🎉"))
    }
  }

  @Test
  fun `save calls setLabel with cleared values when clearLabel is called`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<RecipientId>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = MemberLabelViewModel(memberLabelRepo, groupId, recipientId)
    viewModel.clearLabel()
    viewModel.save()

    coVerify(exactly = 1) {
      memberLabelRepo.setLabel(groupId, MemberLabel(text = "", emoji = null))
    }
  }
}
