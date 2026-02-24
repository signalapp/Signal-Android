package org.thoughtcrime.securesms.conversationlist;

import android.content.Intent;
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
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class ConversationListAdapterAccessibilityActionsInstrumentedTest {

  @Rule
  public final SignalActivityRule harness = new SignalActivityRule(1, false);

  @Test
  public void liveConversationRow_exposesActions_andSelectActionStartsSelectionMode() {
    Recipient other = Recipient.resolved(harness.getOthers().get(0));
    insertIncomingText(other, "conversation list accessibility test");

    ActivityScenario<MainActivity> scenario = ActivityScenario.launch(new Intent(harness.getContext(), MainActivity.class));
    try {
      assertTrue(waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 15_000));

      assertEquals(
          harness.getContext().getString(R.string.ConversationListFragment_select),
          getActionLabel(scenario, R.id.conversation_list_accessibility_select_action)
      );
      assertEquals(
          harness.getContext().getString(R.string.ConversationListFragment_archive),
          getActionLabel(scenario, R.id.conversation_list_accessibility_archive_action)
      );
      assertEquals(
          harness.getContext().getString(R.string.ConversationListFragment_delete),
          getActionLabel(scenario, R.id.conversation_list_accessibility_delete_action)
      );

      assertTrue(performAction(scenario, R.id.conversation_list_accessibility_select_action));
      assertTrue(waitForViewVisible(scenario, R.id.conversation_list_bottom_action_bar, 5_000));
      assertFalse(waitForAction(scenario, R.id.conversation_list_accessibility_select_action, 1_500));
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

  private static boolean waitForAction(@NonNull ActivityScenario<MainActivity> scenario, int actionId, long timeoutMs) {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;

    while (SystemClock.uptimeMillis() < deadline) {
      AtomicBoolean found = new AtomicBoolean(false);

      scenario.onActivity(activity -> {
        RecyclerView recycler = activity.findViewById(R.id.list);
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

  private static @Nullable String getActionLabel(@NonNull ActivityScenario<MainActivity> scenario, int actionId) {
    AtomicReference<String> label = new AtomicReference<>();

    scenario.onActivity(activity -> {
      RecyclerView recycler = activity.findViewById(R.id.list);
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

  private static boolean performAction(@NonNull ActivityScenario<MainActivity> scenario, int actionId) {
    AtomicBoolean performed = new AtomicBoolean(false);

    scenario.onActivity(activity -> {
      RecyclerView recycler = activity.findViewById(R.id.list);
      View child = findChildWithAction(recycler, actionId);
      if (child != null) {
        performed.set(child.performAccessibilityAction(actionId, Bundle.EMPTY));
      }
    });

    return performed.get();
  }

  private static boolean waitForViewVisible(@NonNull ActivityScenario<MainActivity> scenario, int viewId, long timeoutMs) {
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
