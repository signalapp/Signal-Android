/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.thoughtcrime.securesms.conversation.colors.NameColor
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelUiState.SaveState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.whispersystems.signalservice.api.NetworkResult
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MemberLabelViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val memberLabelRepo = mockk<MemberLabelRepository>(relaxUnitFun = true)
  private val groupId = mockk<GroupId.V2>()
  private val recipientId = RecipientId.from(1L)

  @Before
  fun setUp() {
    coEvery { memberLabelRepo.getRecipient(any()) } returns mockk(relaxed = true)
    coEvery { memberLabelRepo.getSenderNameColor(any(), any()) } returns NameColor(0, 0)
    every { memberLabelRepo.hasDismissedMemberLabelAboutOverrideWarning() } returns false
  }

  private fun createViewModel() = MemberLabelViewModel(
    memberLabelRepo = memberLabelRepo,
    groupId = groupId,
    recipientId = recipientId,
    sanitizeEmoji = { emoji -> emoji.takeIf { it.isNotBlank() } }
  )

  @Test
  fun `isSaveEnabled returns true when label text is different from the original value`() {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("Modified")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when label text is the same as the original value`() {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("Original")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when label text is valid and the emoji is different from the original value`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = createViewModel()
    viewModel.onLabelEmojiChanged("🎉")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when the label and emoji are not changed`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = "🎉", text = "Label")

    val viewModel = createViewModel()

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when the label and emoji are changed to the original value`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = createViewModel()

    viewModel.onLabelEmojiChanged("🫢")
    viewModel.onLabelTextChanged("Modified")

    viewModel.onLabelEmojiChanged("🎉")
    viewModel.onLabelTextChanged("Original")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when label is too short`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("")
    viewModel.onLabelEmojiChanged("🎉")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when clearLabel is called with existing label and emoji`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = createViewModel()
    viewModel.clearLabel()

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when clearLabel is called with existing label without emoji`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = createViewModel()
    viewModel.clearLabel()

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when clearLabel is called with no existing label`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.clearLabel()

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when both emoji and label are modified`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = "🎉", text = "Original")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.onLabelEmojiChanged("🚀")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when only emoji is changed without an existing label`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onLabelEmojiChanged("🎉")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `save does not call setLabel when isSaveEnabled is false`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = createViewModel()
    viewModel.save()

    coVerify(exactly = 0) { memberLabelRepo.setLabel(groupId, any()) }
  }

  @Test
  fun `save does not call setLabel when label is less than 1 character`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Label")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("")
    viewModel.onLabelEmojiChanged("🎉")
    viewModel.save()

    coVerify(exactly = 0) { memberLabelRepo.setLabel(groupId, any()) }
  }

  @Test
  fun `save calls setLabel with truncated label when label exceeds max length`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)

    val viewModel = createViewModel()
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
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onLabelEmojiChanged("🎉")
    viewModel.save()

    coVerify(exactly = 0) { memberLabelRepo.setLabel(groupId, any()) }
  }

  @Test
  fun `save calls setLabel when label change is valid`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.onLabelEmojiChanged("🎉")
    viewModel.save()

    coVerify(exactly = 1) {
      memberLabelRepo.setLabel(groupId, MemberLabel(text = "New Label", emoji = "🎉"))
    }
  }

  @Test
  fun `save calls setLabel with cleared values when clearLabel is called`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = "🎉", text = "Original")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)

    val viewModel = createViewModel()
    viewModel.clearLabel()
    viewModel.save()

    coVerify(exactly = 1) {
      memberLabelRepo.setLabel(groupId, MemberLabel(text = "", emoji = null))
    }
  }

  @Test
  fun `onLabelTextChanged counts emoji as single grapheme`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    val emoji = "\uD83C\uDF89" // 🎉
    viewModel.onLabelTextChanged(emoji.repeat(30))

    assertEquals(emoji.repeat(24), viewModel.uiState.value.labelText)
  }

  @Test
  fun `remainingCharacters counts emoji as single grapheme`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    val emoji = "\uD83C\uDF89" // 🎉
    viewModel.onLabelTextChanged(emoji.repeat(10))

    assertEquals(14, viewModel.uiState.value.remainingCharacters)
  }

  @Test
  fun `remainingCharacters counts mixed ascii and emoji correctly`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("Hello \uD83C\uDF89") // "Hello 🎉" = 7 graphemes

    assertEquals(17, viewModel.uiState.value.remainingCharacters)
  }

  @Test
  fun `onLabelTextChanged does not truncate text within grapheme limit`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("Short label")

    assertEquals("Short label", viewModel.uiState.value.labelText)
  }

  @Test
  fun `onLabelTextChanged truncates at exactly 24 graphemes with emoji`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    val input = "A".repeat(23) + "\uD83C\uDF89\uD83C\uDF89" // 25 graphemes
    viewModel.onLabelTextChanged(input)

    val expected = "A".repeat(23) + "\uD83C\uDF89" // 24 graphemes
    assertEquals(expected, viewModel.uiState.value.labelText)
  }

  @Test
  fun `isSaveEnabled returns false when the only change is trailing whitespace`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("Original   ")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns false when the only change is leading whitespace`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("   Original")

    assertFalse(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `isSaveEnabled returns true when text differs beyond whitespace`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("  Modified  ")

    assertTrue(viewModel.uiState.value.isSaveEnabled)
  }

  @Test
  fun `save sets saveState to Success when setLabel returns NetworkResult Success`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertEquals(SaveState.Success, viewModel.uiState.value.saveState)
  }

  @Test
  fun `save sets saveState to NetworkError when setLabel returns NetworkResult NetworkError`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.NetworkError(IOException("Network failure"))

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertEquals(SaveState.NetworkError, viewModel.uiState.value.saveState)
  }

  @Test
  fun `save sets saveState to InsufficientRights when setLabel returns ApplicationError with GroupInsufficientRightsException`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = null, text = "Original")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.ApplicationError(GroupInsufficientRightsException(RuntimeException("Insufficient rights (test)")))

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertEquals(SaveState.InsufficientRights, viewModel.uiState.value.saveState)
  }

  @Test
  fun `save shows about override warning when recipient has about text and the warning hasn't been dismissed`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null
    coEvery { memberLabelRepo.getRecipient(any()) } returns Recipient(about = "Some about text")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)
    every { memberLabelRepo.hasDismissedMemberLabelAboutOverrideWarning() } returns false

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertTrue(viewModel.uiState.value.showAboutOverrideSheet)
  }

  @Test
  fun `save shows about override warning when recipient has about emoji and the warning hasn't been dismissed`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null
    coEvery { memberLabelRepo.getRecipient(any()) } returns Recipient(about = null, aboutEmoji = "😎")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)
    every { memberLabelRepo.hasDismissedMemberLabelAboutOverrideWarning() } returns false

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertTrue(viewModel.uiState.value.showAboutOverrideSheet)
  }

  @Test
  fun `save does not show about override warning when label is cleared`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns MemberLabel(emoji = "🎉", text = "Original")
    coEvery { memberLabelRepo.getRecipient(any()) } returns Recipient(about = "Some about text", aboutEmoji = null)
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)
    every { memberLabelRepo.hasDismissedMemberLabelAboutOverrideWarning() } returns false

    val viewModel = createViewModel()
    viewModel.clearLabel()
    viewModel.save()

    assertFalse(viewModel.uiState.value.showAboutOverrideSheet)
    assertEquals(SaveState.Success, viewModel.uiState.value.saveState)
  }

  @Test
  fun `save does not show about override warning when recipient has no about text or emoji`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null
    coEvery { memberLabelRepo.getRecipient(any()) } returns Recipient(about = null, aboutEmoji = null)
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertFalse(viewModel.uiState.value.showAboutOverrideSheet)
    assertEquals(SaveState.Success, viewModel.uiState.value.saveState)
  }

  @Test
  fun `save does not show about override warning if the warning has been dismissed`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null
    coEvery { memberLabelRepo.getRecipient(any()) } returns Recipient(about = "Some about text")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)
    every { memberLabelRepo.hasDismissedMemberLabelAboutOverrideWarning() } returns true

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertFalse(viewModel.uiState.value.showAboutOverrideSheet)
    assertEquals(SaveState.Success, viewModel.uiState.value.saveState)
  }

  @Test
  fun `onAboutOverrideSheetShown resets showAboutOverrideSheet`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null
    coEvery { memberLabelRepo.getRecipient(any()) } returns Recipient(about = "Some about text")
    coEvery { memberLabelRepo.setLabel(any(), any()) } returns NetworkResult.Success(Unit)
    every { memberLabelRepo.hasDismissedMemberLabelAboutOverrideWarning() } returns false

    val viewModel = createViewModel()
    viewModel.onLabelTextChanged("New Label")
    viewModel.save()

    assertTrue(viewModel.uiState.value.showAboutOverrideSheet)
    viewModel.onAboutOverrideSheetShown()
    assertFalse(viewModel.uiState.value.showAboutOverrideSheet)
  }

  @Test
  fun `onAboutOverrideSheetDismissed sets saveState to Success`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onAboutOverrideSheetDismissed(dontShowAgain = false)

    assertEquals(SaveState.Success, viewModel.uiState.value.saveState)
  }

  @Test
  fun `onAboutOverrideSheetDismissed marks about override warning as dismissed when dontShowAgain = true`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onAboutOverrideSheetDismissed(dontShowAgain = true)

    verify(exactly = 1) { memberLabelRepo.markMemberLabelAboutOverrideWarningDismissed() }
  }

  @Test
  fun `onAboutOverrideSheetDismissed does not mark about override warning as dismissed when dontShowAgain = false`() = runTest(testDispatcher) {
    coEvery { memberLabelRepo.getLabel(groupId, any<Recipient>()) } returns null

    val viewModel = createViewModel()
    viewModel.onAboutOverrideSheetDismissed(dontShowAgain = false)

    verify(exactly = 0) { memberLabelRepo.markMemberLabelAboutOverrideWarningDismissed() }
  }
}
