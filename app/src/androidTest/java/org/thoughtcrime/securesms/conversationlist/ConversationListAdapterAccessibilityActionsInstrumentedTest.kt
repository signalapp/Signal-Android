/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.StoryType
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.testing.DisableAnimationsRule
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ConversationListAdapterAccessibilityActionsInstrumentedTest {

  @Rule
  @JvmField
  val disableAnimationsRule = DisableAnimationsRule()

  @Rule
  @JvmField
  val harness = SignalActivityRule(1, false)

  @Test
  fun conversationRow_selectActionStartsSelectionMode() {
    val other = Recipient.resolved(harness.others.first())
    insertIncomingText(other, "conversation list accessibility test")

    val scenario: ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(harness.context, MainActivity::class.java))
    try {
      assertTrue(waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 15_000))

      assertEquals(
        harness.context.resources.getQuantityString(R.plurals.ConversationListFragment_read_plural, 1),
        getActionLabel(scenario, R.id.conversation_list_accessibility_read_action)
      )
      assertEquals(
        harness.context.getString(R.string.ConversationListFragment_pin),
        getActionLabel(scenario, R.id.conversation_list_accessibility_pin_action)
      )
      assertEquals(
        harness.context.getString(R.string.ConversationListFragment_mute),
        getActionLabel(scenario, R.id.conversation_list_accessibility_mute_action)
      )
      assertEquals(
        harness.context.getString(R.string.ConversationListFragment_select),
        getActionLabel(scenario, R.id.conversation_list_accessibility_select_action)
      )
      assertEquals(
        harness.context.getString(R.string.ConversationListFragment_archive),
        getActionLabel(scenario, R.id.conversation_list_accessibility_archive_action)
      )
      assertEquals(
        harness.context.getString(R.string.ConversationListFragment_delete),
        getActionLabel(scenario, R.id.conversation_list_accessibility_delete_action)
      )

      assertTrue(performAction(scenario, R.id.conversation_list_accessibility_select_action))
      assertTrue(waitForViewVisible(scenario, R.id.conversation_list_bottom_action_bar, 5_000))
      assertFalse(waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 300))
    } finally {
      scenario.close()
    }
  }

  @Test
  fun archivedConversationRow_hidesReadPinMuteAndShowsUnarchive() {
    val other = Recipient.resolved(harness.others.first())
    val threadId = insertIncomingText(other, "archived conversation test")

    // Archive the conversation
    SignalDatabase.threads.setArchived(setOf(threadId), true)

    val scenario: ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(harness.context, MainActivity::class.java))
    try {
      // Navigate to archive view by clicking the "Archived chats" item at the top of the list
      scenario.onActivity { activity ->
        val recycler = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
        // Find the ConversationListItemAction view (the "Archived chats" button)
        for (index in 0 until recycler.childCount) {
          val child = recycler.getChildAt(index)
          if (child is org.thoughtcrime.securesms.conversationlist.ConversationListItemAction) {
            child.performClick()
            break
          }
        }
      }

      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      SystemClock.sleep(1_000)

      // Await the archived conversation row to appear
      assertTrue("Archived conversation should be visible in archive view",
        waitForAction(scenario, R.id.conversation_list_accessibility_archive_action, 15_000))

      // Verify the archive action label shows "Unarchive" for archived rows
      assertEquals(
        harness.context.getString(R.string.ConversationListFragment_unarchive),
        getActionLabel(scenario, R.id.conversation_list_accessibility_archive_action)
      )

      // Assert Read, Pin, and Mute action IDs are absent (hidden for archived rows)
      assertFalse("Archived row should not expose read action",
        waitForAction(scenario, R.id.conversation_list_accessibility_read_action, 300))
      assertFalse("Archived row should not expose pin action",
        waitForAction(scenario, R.id.conversation_list_accessibility_pin_action, 300))
      assertFalse("Archived row should not expose mute action",
        waitForAction(scenario, R.id.conversation_list_accessibility_mute_action, 300))

      // Perform the unarchive action
      assertTrue("Should successfully perform unarchive action",
        performAction(scenario, R.id.conversation_list_accessibility_archive_action))

      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      SystemClock.sleep(500)

      // Verify the conversation is no longer visible in archive view (unarchived)
      // Since it's been unarchived, it should disappear from the archive view
      assertFalse("Archived row should disappear after unarchiving",
        waitForAction(scenario, R.id.conversation_list_accessibility_archive_action, 1_000))
    } finally {
      scenario.close()
    }
  }

  @Test
  fun selectionMode_hidesCustomRowActions_restoresOnExit() {
    val other = Recipient.resolved(harness.others.first())
    insertIncomingText(other, "selection mode test")

    val scenario: ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(harness.context, MainActivity::class.java))
    try {
      // Initially in normal mode - verify actions ARE exposed
      assertTrue("Normal mode should expose read action",
        waitForAction(scenario, R.id.conversation_list_accessibility_read_action, 15_000))
      assertTrue("Normal mode should expose pin action",
        waitForAction(scenario, R.id.conversation_list_accessibility_pin_action, 300))
      assertTrue("Normal mode should expose select action",
        waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 300))

      // Enter selection mode via select action
      assertTrue("Should successfully perform select action",
        performAction(scenario, R.id.conversation_list_accessibility_select_action))

      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      SystemClock.sleep(500)

      // Wait for action mode to appear
      assertTrue("Selection mode should show action bar",
        waitForViewVisible(scenario, R.id.conversation_list_bottom_action_bar, 5_000))

      // In selection mode, custom actions should be hidden
      assertFalse("Selection mode should hide read action",
        waitForAction(scenario, R.id.conversation_list_accessibility_read_action, 300))
      assertFalse("Selection mode should hide pin action",
        waitForAction(scenario, R.id.conversation_list_accessibility_pin_action, 300))
      assertFalse("Selection mode should hide select action",
        waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 300))

      // Exit selection mode by pressing back
      scenario.onActivity { activity ->
        activity.onBackPressed()
      }

      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      SystemClock.sleep(500)

      // After exiting selection mode, actions should be restored
      assertTrue("After exiting selection, read action should be restored",
        waitForAction(scenario, R.id.conversation_list_accessibility_read_action, 300))
      assertTrue("After exiting selection, pin action should be restored",
        waitForAction(scenario, R.id.conversation_list_accessibility_pin_action, 300))
      assertTrue("After exiting selection, select action should be restored",
        waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 300))
    } finally {
      scenario.close()
    }
  }

  @Test
  fun searchResultRow_exposesActionsButNotSelect() {
    val other = Recipient.resolved(harness.others.first())
    insertIncomingText(other, "searchable conversation message")

    val scenario: ActivityScenario<MainActivity> = ActivityScenario.launch(Intent(harness.context, MainActivity::class.java))
    try {
      // Wait for the conversation list to load
      assertTrue("Conversation should appear in list",
        waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 15_000))

      // Open search and enter query that matches the conversation
      scenario.onActivity { activity ->
        val toolbarViewModel = androidx.lifecycle.ViewModelProvider(activity)
          .get(org.thoughtcrime.securesms.main.MainToolbarViewModel::class.java)
        toolbarViewModel.setToolbarMode(org.thoughtcrime.securesms.main.MainToolbarMode.SEARCH)
        toolbarViewModel.setSearchQuery(other.getDisplayName(activity))
      }

      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      SystemClock.sleep(1_000)

      // Search results should expose read, pin, mute, archive, delete
      assertTrue("Search result should expose read action",
        waitForAction(scenario, R.id.conversation_list_accessibility_read_action, 10_000))
      assertTrue("Search result should expose pin action",
        waitForAction(scenario, R.id.conversation_list_accessibility_pin_action, 300))
      assertTrue("Search result should expose mute action",
        waitForAction(scenario, R.id.conversation_list_accessibility_mute_action, 300))
      assertTrue("Search result should expose archive action",
        waitForAction(scenario, R.id.conversation_list_accessibility_archive_action, 300))
      assertTrue("Search result should expose delete action",
        waitForAction(scenario, R.id.conversation_list_accessibility_delete_action, 300))

      // Search results should NOT expose select action
      assertFalse("Search result should not expose select action",
        waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 300))
    } finally {
      scenario.close()
    }
  }

  private fun insertIncomingText(other: Recipient, body: String): Long {
    val now = System.currentTimeMillis()
    val message = IncomingMessage(
      MessageType.NORMAL,
      other.id,
      now,
      now,
      now,
      null,
      null,
      body,
      StoryType.NONE,
      null,
      false,
      -1,
      0,
      null,
      false,
      false,
      null,
      null,
      Collections.emptyList(),
      Collections.emptyList(),
      Collections.emptyList(),
      Collections.emptyList(),
      null,
      null,
      false,
      null
    )

    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(other)
    try {
      SignalDatabase.messages.insertMessageInbox(message, threadId).get()
    } catch (e: Exception) {
      throw AssertionError("Failed to insert incoming message for test setup", e)
    }
    return threadId
  }

  companion object {
    private fun waitForAction(scenario: ActivityScenario<MainActivity>, actionId: Int, timeoutMs: Long): Boolean {
      val deadline = SystemClock.uptimeMillis() + timeoutMs
      var delayMs = 10L

      while (SystemClock.uptimeMillis() < deadline) {
        val found = AtomicBoolean(false)

        scenario.onActivity { activity ->
          val recycler = activity.findViewById<RecyclerView>(R.id.list)
          found.set(findChildWithAction(recycler, actionId) != null)
        }

        if (found.get()) {
          return true
        }

        // Check how much time is left
        val timeRemaining = deadline - SystemClock.uptimeMillis()
        if (timeRemaining <= 0) {
          return false
        }

        // Wait for UI idle
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Sleep with exponential backoff: 10ms, 20ms, 40ms, capped at 100ms
        val sleepDuration = minOf(delayMs, timeRemaining)
        if (sleepDuration > 0) {
          SystemClock.sleep(sleepDuration)
        }
        delayMs = minOf(100, delayMs * 2)
      }

      return false
    }

    private fun getActionLabel(scenario: ActivityScenario<MainActivity>, actionId: Int): String? {
      val label = AtomicReference<String?>()

      scenario.onActivity { activity ->
        val recycler = activity.findViewById<RecyclerView>(R.id.list)
        val child = findChildWithAction(recycler, actionId)
        if (child == null) {
          return@onActivity
        }

        val nodeInfo = child.createAccessibilityNodeInfo()
        try {
          val action = findActionById(nodeInfo, actionId)
          label.set(action?.label?.toString())
        } finally {
          nodeInfo.recycle()
        }
      }

      return label.get()
    }

    private fun performAction(scenario: ActivityScenario<MainActivity>, actionId: Int): Boolean {
      val performed = AtomicBoolean(false)

      scenario.onActivity { activity ->
        val recycler = activity.findViewById<RecyclerView>(R.id.list)
        val child = findChildWithAction(recycler, actionId)
        if (child != null) {
          performed.set(child.performAccessibilityAction(actionId, Bundle.EMPTY))
        }
      }

      return performed.get()
    }

    private fun waitForViewVisible(scenario: ActivityScenario<MainActivity>, viewId: Int, timeoutMs: Long): Boolean {
      val deadline = SystemClock.uptimeMillis() + timeoutMs

      while (SystemClock.uptimeMillis() < deadline) {
        val visible = AtomicBoolean(false)

        scenario.onActivity { activity ->
          val view = activity.findViewById<View>(viewId)
          visible.set(view != null && view.isShown)
        }

        if (visible.get()) {
          return true
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(100)
      }

      return false
    }

    private fun findChildWithAction(recycler: RecyclerView?, actionId: Int): View? {
      if (recycler == null) {
        return null
      }

      for (index in 0 until recycler.childCount) {
        val child = recycler.getChildAt(index)
        val nodeInfo = child.createAccessibilityNodeInfo()
        try {
          if (findActionById(nodeInfo, actionId) != null) {
            return child
          }
        } finally {
          nodeInfo.recycle()
        }
      }

      return null
    }

    private fun findActionById(info: AccessibilityNodeInfo, actionId: Int): AccessibilityNodeInfo.AccessibilityAction? {
      val actionList = info.actionList ?: return null

      for (action in actionList) {
        if (action.id == actionId) {
          return action
        }
      }

      return null
    }
  }
}
