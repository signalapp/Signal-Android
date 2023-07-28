package org.thoughtcrime.securesms.util;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

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
        LocalMetrics.getInstance().cancel(otherId);
      } else {
        LocalMetrics.getInstance().split(otherId, SPLIT_RENDER);
        LocalMetrics.getInstance().end(otherId);
        LocalMetrics.getInstance().cancel(conversationListId);
      }
    }
  }

  public static final class ConversationOpen {
    private static final String NAME = "conversation-open";

    private static final String SPLIT_VIEWMODEL_INIT  = "viewmodel-init";
    private static final String SPLIT_METADATA_LOADED = "metadata-loaded";
    private static final String SPLIT_DATA_LOADED     = "data-loaded";
    private static final String SPLIT_DATA_POSTED     = "data-posted";
    private static final String SPLIT_RENDER          = "render";

    private static String id;

    public static void start() {
      SignalTrace.beginSection("6-ConversationOpen");
      id = NAME + "-" + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
      SignalTrace.beginSection("1-ConversationOpen-ViewModel-Init");
    }

    public static void onMetadataLoadStarted() {
      SignalTrace.endSection();
      LocalMetrics.getInstance().split(id, SPLIT_VIEWMODEL_INIT);
      SignalTrace.beginSection("2-ConversationOpen-Metadata-Loaded");
    }

    public static void onMetadataLoaded() {
      SignalTrace.endSection();
      LocalMetrics.getInstance().split(id, SPLIT_METADATA_LOADED);
      SignalTrace.beginSection("3-ConversationOpen-Data-Loaded");
    }

    public static void onDataLoaded() {
      SignalTrace.endSection();
      LocalMetrics.getInstance().split(id, SPLIT_DATA_LOADED);
      SignalTrace.beginSection("4-ConversationOpen-Data-Posted");
    }

    public static void onDataPostedToMain() {
      SignalTrace.endSection();
      LocalMetrics.getInstance().split(id, SPLIT_DATA_POSTED);
      SignalTrace.beginSection("5-ConversationOpen-Render");
    }

    public static void onRenderFinished() {
      SignalTrace.endSection();
      LocalMetrics.getInstance().split(id, SPLIT_RENDER);
      LocalMetrics.getInstance().end(id);
      SignalTrace.endSection();
    }
  }

  public static final class IndividualMessageSend {
    private static final String NAME = "individual-message-send";

    private static final String SPLIT_DB_INSERT        = "db-insert";
    private static final String SPLIT_JOB_ENQUEUE      = "job-enqueue";
    private static final String SPLIT_JOB_PRE_NETWORK  = "job-pre-network";
    private static final String SPLIT_ENCRYPT          = "encrypt";
    private static final String SPLIT_NETWORK_MAIN     = "network-main";
    private static final String SPLIT_NETWORK_SYNC     = "network-sync";
    private static final String SPLIT_JOB_POST_NETWORK = "job-post-network";
    private static final String SPLIT_UI_UPDATE        = "ui-update";

    private static final Map<Long, String> ID_MAP = new HashMap<>();

    public static @NonNull String start() {
      String id = NAME + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
      return id;
    }

    public static void onInsertedIntoDatabase(long messageId, String id) {
      if (id != null) {
        ID_MAP.put(messageId, id);
        split(messageId, SPLIT_DB_INSERT);
      }
    }

    public static void onJobStarted(long messageId) {
      split(messageId, SPLIT_JOB_ENQUEUE);
    }

    public static void onDeliveryStarted(long messageId) {
      split(messageId, SPLIT_JOB_PRE_NETWORK);
    }

    public static void onMessageEncrypted(long messageId) {
      split(messageId, SPLIT_ENCRYPT);
    }

    public static void onMessageSent(long messageId) {
      split(messageId, SPLIT_NETWORK_MAIN);
    }

    public static void onSyncMessageSent(long messageId) {
      split(messageId, SPLIT_NETWORK_SYNC);
    }

    public static void onJobFinished(long messageId) {
      split(messageId, SPLIT_JOB_POST_NETWORK);
    }

    public static void onUiUpdated(long messageId) {
      split(messageId, SPLIT_UI_UPDATE);
      end(messageId);

      ID_MAP.remove(messageId);
    }

    public static void cancel(long messageId) {
      String splitId = ID_MAP.get(messageId);
      if (splitId != null) {
        LocalMetrics.getInstance().cancel(splitId);
      }

      ID_MAP.remove(messageId);
    }

    private static void split(long messageId, @NonNull String event) {
      String splitId = ID_MAP.get(messageId);
      if (splitId != null) {
        LocalMetrics.getInstance().split(splitId, event);
      }
    }

    private static void end(long messageId) {
      String splitId = ID_MAP.get(messageId);
      if (splitId != null) {
        LocalMetrics.getInstance().end(splitId);
      }
    }
  }

  public static final class MessageLatency {
    public static final String NAME = "message-latency";

    private static final String SPLIT_LATENCY = "latency";

    public static void onMessageReceived(long serverReceiveTimestamp, long serverDeliverTimestamp) {
      long latency = serverDeliverTimestamp - serverReceiveTimestamp;

      String id = NAME + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
      LocalMetrics.getInstance().splitWithDuration(id, SPLIT_LATENCY, latency);
      LocalMetrics.getInstance().end(id);
    }
  }

  public static final class FcmServiceStartFailure {
    public static final String NAME = "fcm-service-start-failure";

    private static final String SPLIT_EVENT = "event";

    public static void onFcmFailedToStart() {
      String id = NAME + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
      LocalMetrics.getInstance().splitWithDuration(id, SPLIT_EVENT, 1);
      LocalMetrics.getInstance().end(id);
    }

  }

  public static final class FcmServiceStartSuccess {
    public static final String NAME = "fcm-service-start-success";

    private static final String SPLIT_EVENT = "event";

    public static void onFcmStarted() {
      String id = NAME + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
      LocalMetrics.getInstance().splitWithDuration(id, SPLIT_EVENT, 1);
      LocalMetrics.getInstance().end(id);
    }

  }
  public static final class PushWebsocketFetch {
    public static final String SUCCESS_EVENT = "push-websocket-fetch";
    public static final String TIMEOUT_EVENT = "timed-out-fetch";

    private static final String SPLIT_BATCH_PROCESSED = "batches-processed";
    private static final String SPLIT_PROCESS_TIME    = "fetch-time";
    private static final String SPLIT_TIMED_OUT = "timeout";

    private static final AtomicInteger processedBatches = new AtomicInteger(0);

    public static @NonNull String startFetch() {
      String baseId = System.currentTimeMillis() + "";

      String timeoutId = TIMEOUT_EVENT + baseId;
      String successId = SUCCESS_EVENT + baseId;

      LocalMetrics.getInstance().start(successId, SUCCESS_EVENT);
      LocalMetrics.getInstance().start(timeoutId, TIMEOUT_EVENT);
      processedBatches.set(0);

      return baseId;
    }

    public static void onProcessedBatch() {
      processedBatches.incrementAndGet();
    }

    public static void onTimedOut(String metricId) {
      LocalMetrics.getInstance().cancel(SUCCESS_EVENT + metricId);

      String timeoutId = TIMEOUT_EVENT + metricId;

      LocalMetrics.getInstance().split(timeoutId, SPLIT_TIMED_OUT);
      LocalMetrics.getInstance().end(timeoutId);
    }

    public static void onDrained(String metricId) {
      LocalMetrics.getInstance().cancel(TIMEOUT_EVENT + metricId);

      String successId = SUCCESS_EVENT + metricId;

      LocalMetrics.getInstance().split(successId, SPLIT_PROCESS_TIME);
      LocalMetrics.getInstance().splitWithDuration(successId, SPLIT_BATCH_PROCESSED, processedBatches.get());
      LocalMetrics.getInstance().end(successId);
    }

  }

  public static final class GroupMessageSend {
    private static final String NAME = "group-message-send";

    private static final String SPLIT_DB_INSERT               = "db-insert";
    private static final String SPLIT_JOB_ENQUEUE             = "job-enqueue";
    private static final String SPLIT_JOB_PRE_NETWORK         = "job-pre-network";
    private static final String SPLIT_SENDER_KEY_SHARED       = "sk-shared";
    private static final String SPLIT_ENCRYPTION              = "encryption";
    private static final String SPLIT_NETWORK_SENDER_KEY      = "network-sk";
    private static final String SPLIT_NETWORK_SENDER_KEY_SYNC = "network-sk-sync";
    private static final String SPLIT_MSL_SENDER_KEY          = "msl-sk";
    private static final String SPLIT_NETWORK_LEGACY          = "network-legacy";
    private static final String SPLIT_NETWORK_LEGACY_SYNC     = "network-legacy-sync";
    private static final String SPLIT_JOB_POST_NETWORK        = "job-post-network";
    private static final String SPLIT_UI_UPDATE               = "ui-update";

    private static final Map<Long, String> ID_MAP = new HashMap<>();

    public static @NonNull String start() {
      String id = NAME + System.currentTimeMillis();
      LocalMetrics.getInstance().start(id, NAME);
      return id;
    }

    public static void onInsertedIntoDatabase(long messageId, String id) {
      if (id != null) {
        ID_MAP.put(messageId, id);
        split(messageId, SPLIT_DB_INSERT);
      }
    }

    public static void onJobStarted(long messageId) {
      split(messageId, SPLIT_JOB_ENQUEUE);
    }

    public static void onSenderKeyStarted(long messageId) {
      split(messageId, SPLIT_JOB_PRE_NETWORK);
    }

    public static void onSenderKeyShared(long messageId) {
      split(messageId, SPLIT_SENDER_KEY_SHARED);
    }

    public static void onSenderKeyEncrypted(long messageId) {
      split(messageId, SPLIT_ENCRYPTION);
    }

    public static void onSenderKeyMessageSent(long messageId) {
      split(messageId, SPLIT_NETWORK_SENDER_KEY);
    }

    public static void onSenderKeySyncSent(long messageId) {
      split(messageId, SPLIT_NETWORK_SENDER_KEY_SYNC);
    }

    public static void onSenderKeyMslInserted(long messageId) {
      split(messageId, SPLIT_MSL_SENDER_KEY);
    }

    public static void onLegacyMessageSent(long messageId) {
      split(messageId, SPLIT_NETWORK_LEGACY);
    }

    public static void onLegacySyncFinished(long messageId) {
      split(messageId, SPLIT_NETWORK_LEGACY_SYNC);
    }

    public static void onJobFinished(long messageId) {
      split(messageId, SPLIT_JOB_POST_NETWORK);
    }

    public static void onUiUpdated(long messageId) {
      split(messageId, SPLIT_UI_UPDATE);
      end(messageId);

      ID_MAP.remove(messageId);
    }

    public static void cancel(@Nullable String id) {
      if (id != null) {
        LocalMetrics.getInstance().cancel(id);
      }
    }

    public static void cancel(long messageId) {
      String splitId = ID_MAP.get(messageId);
      if (splitId != null) {
        LocalMetrics.getInstance().cancel(splitId);
      }

      ID_MAP.remove(messageId);
    }

    private static void split(long messageId, @NonNull String event) {
      String splitId = ID_MAP.get(messageId);
      if (splitId != null) {
        LocalMetrics.getInstance().split(splitId, event);
      }
    }

    private static void end(long messageId) {
      String splitId = ID_MAP.get(messageId);
      if (splitId != null) {
        LocalMetrics.getInstance().end(splitId);
      }
    }
  }

  public static final class MessageReceive {
    private static final String NAME_GROUP      = "group-message-receive";
    private static final String NAME_INDIVIDUAL = "individual-message-receive";

    private static final String SPLIT_DECRYPTION        = "decryption";
    private static final String SPLIT_PRE_PROCESS       = "pre-process";
    private static final String SPLIT_GROUPS_PROCESSING = "groups-v2";
    private static final String SPLIT_DB_INSERT_MEDIA   = "media-insert";
    private static final String SPLIT_DB_INSERT_TEXT    = "text-insert";
    private static final String SPLIT_POST_PROCESS      = "post-process";
    private boolean insertedToDb = false;

    private final String individualMetricId;
    private final String groupMetricId;

    public static MessageReceive start() {
      return new MessageReceive();
    }

    private MessageReceive() {
      long time = System.currentTimeMillis();
      individualMetricId = NAME_INDIVIDUAL + time;
      groupMetricId = NAME_GROUP + time;

      LocalMetrics.getInstance().start(individualMetricId, NAME_INDIVIDUAL, TimeUnit.MICROSECONDS);
      LocalMetrics.getInstance().start(groupMetricId, NAME_GROUP, TimeUnit.MICROSECONDS);
    }

    public void onEnvelopeDecrypted() {
      split(SPLIT_DECRYPTION);
    }

    public void onPreProcessComplete() {
      split(SPLIT_PRE_PROCESS);
    }

    public void onInsertedMediaMessage() {
      split(SPLIT_DB_INSERT_MEDIA);
      insertedToDb = true;
    }

    public void onInsertedTextMessage() {
      split(SPLIT_DB_INSERT_TEXT);
      insertedToDb = true;
    }

    public void onPostProcessComplete() {
      split(SPLIT_POST_PROCESS);
    }

    public void onGv2Processed() {
      split(SPLIT_GROUPS_PROCESSING);
    }

    private void split(String name) {
      LocalMetrics.getInstance().split(groupMetricId, name);
      LocalMetrics.getInstance().split(individualMetricId, name);
    }

    public void complete(boolean isGroup) {
      if (!insertedToDb) {
        LocalMetrics.getInstance().cancel(groupMetricId);
        LocalMetrics.getInstance().cancel(individualMetricId);
        return;
      }

      if (isGroup) {
        LocalMetrics.getInstance().cancel(individualMetricId);
        LocalMetrics.getInstance().end(groupMetricId);
      } else {
        LocalMetrics.getInstance().cancel(groupMetricId);
        LocalMetrics.getInstance().end(individualMetricId);
      }
    }
  }
}
