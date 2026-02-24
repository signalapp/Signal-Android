/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.v2.ConversationActivity;
import org.thoughtcrime.securesms.database.MessageType;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.mms.IncomingMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.testing.SignalActivityRule;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class ConversationMessageAccessibilityActionsInstrumentedTest {

  @Rule
  public final SignalActivityRule harness = new SignalActivityRule(1, false);

  @Test
  public void liveMessage_exposesAccessibilityActions_andMultiSelectActionStartsActionMode() {
    Recipient other = Recipient.resolved(harness.getOthers().get(0));
    long threadId = insertIncomingText(other, "conversation message accessibility test");

    ActivityScenario<ConversationActivity> scenario = ActivityScenario.launch(
        ConversationIntents.createBuilderSync(harness.getContext(), other.getId(), threadId).build()
    );

    try {
      assertTrue(waitForAction(scenario, R.id.conversation_message_accessibility_reply_action, 15_000));

      assertEquals(
          harness.getContext().getString(R.string.conversation_selection__menu_reply),
          getActionLabel(scenario, R.id.conversation_message_accessibility_reply_action)
      );
      assertEquals(
          harness.getContext().getString(R.string.conversation_selection__menu_multi_select),
          getActionLabel(scenario, R.id.conversation_message_accessibility_multiselect_action)
      );
      assertEquals(
          harness.getContext().getString(R.string.conversation_selection__menu_delete),
          getActionLabel(scenario, R.id.conversation_message_accessibility_delete_action)
      );

      assertTrue(performAction(scenario, R.id.conversation_message_accessibility_multiselect_action));
      assertTrue(waitForViewVisible(scenario, R.id.action_mode_top_bar, 5_000));
      assertFalse(waitForAction(scenario, R.id.conversation_message_accessibility_multiselect_action, 1_500));
    } finally {
      scenario.close();
    }
  }

  private long insertIncomingText(@NonNull Recipient other, @NonNull String body) {
    long now = System.currentTimeMillis();
    IncomingMessage message = new IncomingMessage(
        MessageType.NORMAL,
        other.getId(),
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
    );

    long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(other);
    try {
      SignalDatabase.messages().insertMessageInbox(message, threadId).get();
    } catch (Exception e) {
      throw new AssertionError("Failed to insert incoming message for test setup", e);
    }
    return threadId;
  }

  private static boolean waitForAction(@NonNull ActivityScenario<ConversationActivity> scenario, int actionId, long timeoutMs) {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;

    while (SystemClock.uptimeMillis() < deadline) {
      AtomicBoolean found = new AtomicBoolean(false);

      scenario.onActivity(activity -> {
        RecyclerView recycler = activity.findViewById(R.id.conversation_item_recycler);
        found.set(findChildWithAction(recycler, actionId) != null);
      });

      if (found.get()) {
        return true;
      }

      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      SystemClock.sleep(100);
    }

    return false;
  }

  private static @Nullable String getActionLabel(@NonNull ActivityScenario<ConversationActivity> scenario, int actionId) {
    AtomicReference<String> label = new AtomicReference<>();

    scenario.onActivity(activity -> {
      RecyclerView recycler = activity.findViewById(R.id.conversation_item_recycler);
      View child = findChildWithAction(recycler, actionId);
      if (child == null) {
        return;
      }

      AccessibilityNodeInfo nodeInfo = child.createAccessibilityNodeInfo();
      try {
        AccessibilityNodeInfo.AccessibilityAction action = findActionById(nodeInfo, actionId);
        label.set(action != null && action.getLabel() != null ? action.getLabel().toString() : null);
      } finally {
        nodeInfo.recycle();
      }
    });

    return label.get();
  }

  private static boolean performAction(@NonNull ActivityScenario<ConversationActivity> scenario, int actionId) {
    AtomicBoolean performed = new AtomicBoolean(false);

    scenario.onActivity(activity -> {
      RecyclerView recycler = activity.findViewById(R.id.conversation_item_recycler);
      View child = findChildWithAction(recycler, actionId);
      if (child != null) {
        performed.set(child.performAccessibilityAction(actionId, Bundle.EMPTY));
      }
    });

    return performed.get();
  }

  private static boolean waitForViewVisible(@NonNull ActivityScenario<ConversationActivity> scenario, int viewId, long timeoutMs) {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;

    while (SystemClock.uptimeMillis() < deadline) {
      AtomicBoolean visible = new AtomicBoolean(false);

      scenario.onActivity(activity -> {
        View view = activity.findViewById(viewId);
        visible.set(view != null && view.isShown());
      });

      if (visible.get()) {
        return true;
      }

      InstrumentationRegistry.getInstrumentation().waitForIdleSync();
      SystemClock.sleep(100);
    }

    return false;
  }

  private static @Nullable View findChildWithAction(@Nullable RecyclerView recycler, int actionId) {
    if (recycler == null) {
      return null;
    }

    for (int index = 0; index < recycler.getChildCount(); index++) {
      View child = recycler.getChildAt(index);
      AccessibilityNodeInfo nodeInfo = child.createAccessibilityNodeInfo();
      try {
        if (findActionById(nodeInfo, actionId) != null) {
          return child;
        }
      } finally {
        nodeInfo.recycle();
      }
    }

    return null;
  }

  private static @Nullable AccessibilityNodeInfo.AccessibilityAction findActionById(@NonNull AccessibilityNodeInfo info, int actionId) {
    if (info.getActionList() == null) {
      return null;
    }

    for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
      if (action.getId() == actionId) {
        return action;
      }
    }

    return null;
  }
}
