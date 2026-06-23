/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.testing.SignalActivityRule
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * End-to-end UI test of the unread divider. Seeds a thread with many unread messages and opens it via the notification
 * path (which enters the conversation with no explicit jump point — functionally "open a chat with X unread"), then
 * verifies the real pipeline (repository -> view model -> fragment -> decoration) anchors the divider to the oldest
 * unread message and scrolls there rather than opening at the bottom.
 *
 * The launch harness mirrors [org.thoughtcrime.securesms.main.MainNavigationLaunchTest]: ActivityScenario can't track
 * MainActivity launched with a custom-action intent, so we start it via Application#startActivity and observe lifecycle
 * callbacks instead.
 */
@RunWith(AndroidJUnit4::class)
class UnreadDividerInstrumentationTest {

  @get:Rule
  val harness = SignalActivityRule(othersCount = 2)

  @Test
  fun opensScrolledToOldestUnreadWithCorrectDividerState() {
    val recipientId = harness.others.first()
    SignalDatabase.recipients.setProfileSharing(recipientId, true)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

    val totalUnread = 50
    val oldestSentTime = 1000L
    var oldestUnreadId = -1L
    for (i in 0 until totalUnread) {
      val id = insertIncoming(threadId, recipientId, time = oldestSentTime + i, body = "unread $i")
      if (i == 0) {
        oldestUnreadId = id
      }
    }

    // Derive expectations from the DB the same way the app does, so the test is robust to any extra system rows.
    val expectedUnreadCount = SignalDatabase.messages.getUnreadCount(threadId)
    val firstUnreadPosition = SignalDatabase.messages.getMessagePositionByDateReceivedTimestamp(threadId, oldestSentTime, false)

    launch(recipientId).use { launched ->
      val result = await(timeoutMs = 20_000, description = "conversation scrolled to oldest unread") {
        val fragment = launched.latestConversationFragment() ?: return@await null
        val recycler = fragment.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler) ?: return@await null
        val decoration = recycler.conversationItemDecorations() ?: return@await null
        val state = decoration.unreadStateForTesting as? ConversationItemDecorations.UnreadState.CompleteUnreadState ?: return@await null
        val view = recycler.layoutManager?.findViewByPosition(firstUnreadPosition) ?: return@await null
        Observed(state.unreadCount, state.firstUnreadId, view.top, recycler.height)
      }

      assertThat(result.unreadCount).isEqualTo(expectedUnreadCount)
      assertThat(result.firstUnreadId).isEqualTo(oldestUnreadId)
      // The oldest unread is laid out in the top half -> we scrolled up to it instead of opening at the bottom (where,
      // with this many messages, it would be off-screen above and findViewByPosition would have returned null).
      assertThat(result.firstUnreadTop).isGreaterThanOrEqualTo(0)
      assertThat(result.firstUnreadTop).isLessThan(result.recyclerHeight / 2)
    }
  }

  @Test
  fun fullyReadConversationOpensAtBottomWithoutDivider() {
    val recipientId = harness.others.first()
    SignalDatabase.recipients.setProfileSharing(recipientId, true)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

    val total = 50
    for (i in 0 until total) {
      insertIncoming(threadId, recipientId, time = 1000L + i, body = "read $i")
    }
    SignalDatabase.threads.setRead(threadId)
    // Precondition: nothing is unread, so there should be no divider.
    assertThat(SignalDatabase.messages.getUnreadCount(threadId)).isEqualTo(0)

    launch(recipientId).use { launched ->
      val result = await(timeoutMs = 20_000, description = "fully-read conversation opened at the bottom") {
        val fragment = launched.latestConversationFragment() ?: return@await null
        val recycler = fragment.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler) ?: return@await null
        val decoration = recycler.conversationItemDecorations() ?: return@await null
        // The newest message is position 0; if it's laid out, the list loaded and settled at the bottom.
        val newest = recycler.layoutManager?.findViewByPosition(0) ?: return@await null
        BottomObserved(decoration.unreadStateForTesting, newest.bottom, recycler.height)
      }

      assertThat(result.unreadState).isEqualTo(ConversationItemDecorations.UnreadState.None)
      // Newest message sits in the lower half -> opened at the bottom (with this many messages it would be off-screen
      // below if we'd opened at the top).
      assertThat(result.newestBottom).isGreaterThan(result.recyclerHeight / 2)
    }
  }

  @Test
  fun outgoingMessageNewerThanUnreadClearsDivider() {
    val recipientId = harness.others.first()
    SignalDatabase.recipients.setProfileSharing(recipientId, true)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

    // A few unread incoming messages, then a newer outgoing reply. Kept small so all rows load in the initial page.
    insertIncoming(threadId, recipientId, time = 1000L, body = "unread 0")
    insertIncoming(threadId, recipientId, time = 1001L, body = "unread 1")
    insertIncoming(threadId, recipientId, time = 1002L, body = "unread 2")
    val outgoing = OutgoingMessage.text(
      threadRecipient = Recipient.resolved(recipientId),
      body = "my reply",
      expiresIn = 0,
      sentTimeMillis = 1003L
    )
    SignalDatabase.messages.insertMessageOutbox(outgoing, threadId)

    // Precondition: the messages are still unread at the DB level, so the divider would show if it weren't for the
    // newer outgoing message clearing it.
    assertThat(SignalDatabase.messages.getUnreadCount(threadId)).isGreaterThan(0)

    launch(recipientId).use { launched ->
      val cleared = await(timeoutMs = 20_000, description = "divider cleared by newer outgoing message") {
        val fragment = launched.latestConversationFragment() ?: return@await null
        val recycler = fragment.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler) ?: return@await null
        val decoration = recycler.conversationItemDecorations() ?: return@await null
        // Wait until the list has loaded (outgoing at position 0 laid out) before reading the resolved state.
        recycler.layoutManager?.findViewByPosition(0) ?: return@await null
        if (decoration.unreadStateForTesting == ConversationItemDecorations.UnreadState.None) true else null
      }

      assertThat(cleared).isEqualTo(true)
    }
  }

  @Test
  fun scrollingToBottomMarksEverythingReadAndDrainsUnreadCount() {
    val recipientId = harness.others.first()
    SignalDatabase.recipients.setProfileSharing(recipientId, true)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

    val total = 50
    for (i in 0 until total) {
      insertIncoming(threadId, recipientId, time = 1000L + i, body = "unread $i")
    }

    launch(recipientId).use { launched ->
      // getUnreadCount is the shared source for the chat-list badge and the scroll-to-bottom button's count, so
      // asserting on it verifies the number the user sees updating as they scroll.
      await(timeoutMs = 20_000, description = "conversation loaded") {
        val recycler = launched.latestConversationFragment()?.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler)
        if ((recycler?.childCount ?: 0) > 0) true else null
      }
      assertThat(SignalDatabase.messages.getUnreadCount(threadId)).isGreaterThan(0)

      // Jump to the newest message; revealing it marks every earlier message read (MarkReadHelper.onViewsRevealed).
      runOnMain {
        launched.latestConversationFragment()?.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler)?.scrollToPosition(0)
      }

      // Scrolling through the thread drains the unread count to 0.
      await(timeoutMs = 20_000, description = "unread count reaches 0 after scrolling to the bottom") {
        if (SignalDatabase.messages.getUnreadCount(threadId) == 0) true else null
      }
    }
  }

  @Test
  fun scrollingPartwayLeavesExactlyTheUnreadMessagesBelowTheViewport() {
    val recipientId = harness.others.first()
    SignalDatabase.recipients.setProfileSharing(recipientId, true)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))

    val total = 50
    for (i in 0 until total) {
      insertIncoming(threadId, recipientId, time = 1000L + i, body = "unread $i")
    }

    launch(recipientId).use { launched ->
      await(timeoutMs = 20_000, description = "conversation loaded") {
        val recycler = launched.latestConversationFragment()?.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler)
        if ((recycler?.childCount ?: 0) > 0) true else null
      }

      // The chat opens at the oldest unread (near the top); scroll down to roughly the middle.
      runOnMain {
        val recycler = launched.latestConversationFragment()?.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler)
        (recycler?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(total / 2, 0)
      }

      // Once mark-read settles, the unread count must equal the index of the newest visible message — i.e. exactly the
      // messages still below the viewport (reverse layout: position 0 = newest, so index N = N newer messages). This is
      // the number the scroll-to-bottom button and chat-list badge show; it must not over- or under-count mid-scroll.
      val stableCount = awaitStableUnreadCount(threadId)
      val newestVisiblePosition = await(timeoutMs = 5_000, description = "newest visible position") {
        val recycler = launched.latestConversationFragment()?.view?.findViewById<RecyclerView>(R.id.conversation_item_recycler)
        (recycler?.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()?.takeIf { it >= 0 }
      }

      assertThat(stableCount).isEqualTo(newestVisiblePosition)
      // Sanity: we exercised a genuine mid-scroll point, not the very top or bottom.
      assertThat(stableCount).isGreaterThan(0)
      assertThat(stableCount).isLessThan(total)
    }
  }

  /** Polls [MessageTable.getUnreadCount] until it holds steady (mark-read is debounced + async), then returns it. */
  private fun awaitStableUnreadCount(threadId: Long, timeoutMs: Long = 20_000): Int {
    val deadline = System.currentTimeMillis() + timeoutMs
    var last = Int.MIN_VALUE
    var stableSince = System.currentTimeMillis()
    while (System.currentTimeMillis() < deadline) {
      val current = SignalDatabase.messages.getUnreadCount(threadId)
      if (current == last) {
        if (System.currentTimeMillis() - stableSince >= 500) {
          return current
        }
      } else {
        last = current
        stableSince = System.currentTimeMillis()
      }
      Thread.sleep(100)
    }
    throw AssertionError("Unread count never stabilized (last observed = $last)")
  }

  private data class BottomObserved(
    val unreadState: ConversationItemDecorations.UnreadState,
    val newestBottom: Int,
    val recyclerHeight: Int
  )

  private fun insertIncoming(threadId: Long, from: RecipientId, time: Long, body: String): Long {
    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = from,
      sentTimeMillis = time,
      serverTimeMillis = time,
      receivedTimeMillis = time,
      body = body
    )
    return SignalDatabase.messages.insertMessageInbox(message, threadId).get().messageId
  }

  private data class Observed(
    val unreadCount: Int,
    val firstUnreadId: Long,
    val firstUnreadTop: Int,
    val recyclerHeight: Int
  )

  private fun RecyclerView.conversationItemDecorations(): ConversationItemDecorations? {
    for (i in 0 until itemDecorationCount) {
      val decoration = getItemDecorationAt(i)
      if (decoration is ConversationItemDecorations) {
        return decoration
      }
    }
    return null
  }

  private fun runOnMain(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
  }

  /** Polls [block] on the main thread until it returns non-null, failing after [timeoutMs]. */
  private fun <T> await(timeoutMs: Long, pollMs: Long = 100, description: String, block: () -> T?): T {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      var value: T? = null
      InstrumentationRegistry.getInstrumentation().runOnMainSync { value = block() }
      if (value != null) {
        return value!!
      }
      Thread.sleep(pollMs)
    }
    throw AssertionError("Timed out after ${timeoutMs}ms waiting for $description")
  }

  private fun launch(recipientId: RecipientId): Launched {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    val resumed = CountDownLatch(1)
    val conversationFragments: MutableList<ConversationFragment> = Collections.synchronizedList(mutableListOf())
    val allActivities: MutableList<Activity> = Collections.synchronizedList(mutableListOf())

    val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
      override fun onFragmentCreated(fm: FragmentManager, f: Fragment, savedInstanceState: Bundle?) {
        if (f is ConversationFragment) {
          conversationFragments.add(f)
        }
      }

      override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
        if (f is ConversationFragment) {
          conversationFragments.remove(f)
        }
      }
    }

    val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        allActivities.add(activity)
        if (activity is MainActivity) {
          activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
        }
      }

      override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
          resumed.countDown()
        }
      }

      override fun onActivityStarted(activity: Activity) = Unit
      override fun onActivityPaused(activity: Activity) = Unit
      override fun onActivityStopped(activity: Activity) = Unit
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
      override fun onActivityDestroyed(activity: Activity) {
        allActivities.remove(activity)
      }
    }
    app.registerActivityLifecycleCallbacks(activityCallbacks)

    // Open the conversation the way a notification tap does: a conversation intent with no starting position.
    val conversationIntent = ConversationIntents.createBuilder(harness.context, recipientId, -1L).blockingGet().build()
    val intent = Intent(harness.context, MainActivity::class.java).apply {
      action = ConversationIntents.ACTION
      putExtras(conversationIntent)
      // Application#startActivity from a non-Activity context requires a new task.
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
      app.startActivity(intent)
    } catch (t: Throwable) {
      app.unregisterActivityLifecycleCallbacks(activityCallbacks)
      throw t
    }

    if (!resumed.await(15, TimeUnit.SECONDS)) {
      app.unregisterActivityLifecycleCallbacks(activityCallbacks)
      throw AssertionError("MainActivity did not reach RESUMED within 15s")
    }

    return Launched(conversationFragments, app, activityCallbacks, allActivities)
  }

  private class Launched(
    private val conversationFragments: List<ConversationFragment>,
    private val app: Application,
    private val callbacks: Application.ActivityLifecycleCallbacks,
    private val allActivities: MutableList<Activity>
  ) : AutoCloseable {

    fun latestConversationFragment(): ConversationFragment? = synchronized(conversationFragments) { conversationFragments.lastOrNull() }

    override fun close() {
      val toFinish = synchronized(allActivities) { allActivities.toList() }
      if (toFinish.isNotEmpty()) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
          toFinish.forEach { it.finish() }
        }
      }
      app.unregisterActivityLifecycleCallbacks(callbacks)
    }
  }
}
