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
      conversationListId = NAME_CONVERSATION_LIST + "-" + System.currentTimeMillis();
      otherId            = NAME_OTHER + "-" + System.currentTimeMillis();

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

  public static final class IndividualMessageSend {
    private static final String NAME = "individual-message-send";

    private static final String SPLIT_JOB_ENQUEUE      = "job-enqueue";
    private static final String SPLIT_JOB_PRE_NETWORK  = "job-pre-network";
    private static final String SPLIT_NETWORK          = "network";
    private static final String SPLIT_JOB_POST_NETWORK = "job-post-network";
    private static final String SPLIT_UI_UPDATE        = "ui-update";

    public static void start(long messageId) {
      LocalMetrics.getInstance().start(buildId(messageId), NAME);
    }

    public static void onJobStarted(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_JOB_ENQUEUE);
    }

    public static void onNetworkStarted(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_JOB_PRE_NETWORK);
    }

    public static void onNetworkFinished(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_NETWORK);
    }

    public static void onJobFinished(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_JOB_POST_NETWORK);
    }

    public static void onUiUpdated(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_UI_UPDATE);
      LocalMetrics.getInstance().end(buildId(messageId));
    }

    private static String buildId(long messageId) {
      return NAME + "-" + messageId;
    }
  }

  public static final class ConversationOpen {
    private static final String NAME = "conversation-open";

    private static final String SPLIT_DATA_LOADED = "data-loaded";
    private static final String SPLIT_RENDER      = "render";

    private static String id;

    public static void start() {
      id = NAME + "-" + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
    }

    public static void onDataLoaded() {
      LocalMetrics.getInstance().split(id, SPLIT_DATA_LOADED);
    }

    public static void onRenderFinished() {
      LocalMetrics.getInstance().split(id, SPLIT_RENDER);
      LocalMetrics.getInstance().end(id);
    }
  }

  public static final class GroupMessageSend {
    private static final String NAME = "group-message-send";

    private static final String SPLIT_JOB_ENQUEUE      = "job-enqueue";
    private static final String SPLIT_JOB_PRE_NETWORK  = "job-pre-network";
    private static final String SPLIT_NETWORK          = "network";
    private static final String SPLIT_JOB_POST_NETWORK = "job-post-network";
    private static final String SPLIT_UI_UPDATE        = "ui-update";

    public static void start(long messageId) {
      LocalMetrics.getInstance().start(buildId(messageId), NAME);
    }

    public static void onJobStarted(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_JOB_ENQUEUE);
    }

    public static void onNetworkStarted(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_JOB_PRE_NETWORK);
    }

    public static void onNetworkFinished(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_NETWORK);
    }

    public static void onJobFinished(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_JOB_POST_NETWORK);
    }

    public static void onUiUpdated(long messageId) {
      LocalMetrics.getInstance().split(buildId(messageId), SPLIT_UI_UPDATE);
      LocalMetrics.getInstance().end(buildId(messageId));
    }

    private static String buildId(long messageId) {
      return NAME + "-" + messageId;
    }
  }
}
