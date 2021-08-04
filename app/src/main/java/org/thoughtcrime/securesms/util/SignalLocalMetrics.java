package org.thoughtcrime.securesms.util;

import androidx.annotation.MainThread;

/**
 * A nice interface for {@link LocalMetrics} that gives us a place to define string constants and nicer method names.
 */
public final class SignalLocalMetrics {

  private SignalLocalMetrics() {}

  public static final class ColdStart {
    private static final String NAME_CONVERSATION_LIST = "cold-start-conversation-list";
    private static final String NAME_OTHER             = "cold-start-other";

    private static final String SPLIT_APPLICATION_CREATE = "application-create";
    private static final String SPLIT_ACTIVITY_CREATE    = "start-activity";
    private static final String SPLIT_DATA_LOADED        = "data-loaded";
    private static final String SPLIT_RENDER             = "render";

    private static String conversationListId;
    private static String otherId;

    private static boolean isConversationList;

    @MainThread
    public static void start() {
      conversationListId = NAME_CONVERSATION_LIST + System.currentTimeMillis();
      otherId            = NAME_OTHER + System.currentTimeMillis();

      LocalMetrics.getInstance().start(conversationListId, NAME_CONVERSATION_LIST);
      LocalMetrics.getInstance().start(otherId, NAME_OTHER);
    }

    @MainThread
    public static void onApplicationCreateFinished() {
      LocalMetrics.getInstance().split(conversationListId, SPLIT_APPLICATION_CREATE);
      LocalMetrics.getInstance().split(otherId, SPLIT_APPLICATION_CREATE);
    }

    @MainThread
    public static void onRenderStart() {
      LocalMetrics.getInstance().split(conversationListId, SPLIT_ACTIVITY_CREATE);
      LocalMetrics.getInstance().split(otherId, SPLIT_ACTIVITY_CREATE);
    }

    @MainThread
    public static void onConversationListDataLoaded() {
      isConversationList = true;
      LocalMetrics.getInstance().split(conversationListId, SPLIT_DATA_LOADED);
    }

    @MainThread
    public static void onRenderFinished() {
      if (isConversationList) {
        LocalMetrics.getInstance().split(conversationListId, SPLIT_RENDER);
        LocalMetrics.getInstance().end(conversationListId);
        LocalMetrics.getInstance().drop(otherId);
      } else {
        LocalMetrics.getInstance().split(otherId, SPLIT_RENDER);
        LocalMetrics.getInstance().end(otherId);
        LocalMetrics.getInstance().drop(conversationListId);
      }
    }
  }
}
