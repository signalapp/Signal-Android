package org.thoughtcrime.securesms.notifications;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.MessageTable.ExpirationInfo;
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.CallLogEventSendJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MarkReadReceiver extends BroadcastReceiver {

  private static final String TAG                   = Log.tag(MarkReadReceiver.class);
  public static final  String CLEAR_ACTION          = "org.thoughtcrime.securesms.notifications.CLEAR";
  public static final  String THREADS_EXTRA         = "threads";
  public static final  String NOTIFICATION_ID_EXTRA = "notification_id";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final ArrayList<ConversationId> threads = intent.getParcelableArrayListExtra(THREADS_EXTRA);

    if (threads != null) {
      MessageNotifier notifier = AppDependencies.getMessageNotifier();
      for (ConversationId thread : threads) {
        notifier.removeStickyThread(thread);
      }

      NotificationCancellationHelper.cancelLegacy(context, intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1));

      PendingResult finisher = goAsync();
      SignalExecutors.BOUNDED.execute(() -> {
        List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();

        for (ConversationId thread : threads) {
          Log.i(TAG, "Marking as read: " + thread);
          List<MarkedMessageInfo> messageIds = SignalDatabase.threads().setRead(thread, true);
          messageIdsCollection.addAll(messageIds);
        }

        process(messageIdsCollection);
        processCallEvents(threads, System.currentTimeMillis());

        AppDependencies.getMessageNotifier().updateNotification(context);
        finisher.finish();
      });
    }
  }

  public static void process(@NonNull List<MarkedMessageInfo> markedReadMessages) {
    if (markedReadMessages.isEmpty()) return;

    List<SyncMessageId>  syncMessageIds = Stream.of(markedReadMessages)
                                                .map(MarkedMessageInfo::getSyncMessageId)
                                                .toList();
    List<ExpirationInfo> expirationInfo = Stream.of(markedReadMessages)
                                                .map(MarkedMessageInfo::getExpirationInfo)
                                                .filter(info -> info.getExpiresIn() > 0 && info.getExpireStarted() <= 0)
                                                .toList();

    scheduleDeletion(expirationInfo);

    MultiDeviceReadUpdateJob.enqueue(syncMessageIds);

    Map<Long, List<MarkedMessageInfo>> threadToInfo = Stream.of(markedReadMessages)
                                                            .collect(Collectors.groupingBy(MarkedMessageInfo::getThreadId));

    Stream.of(threadToInfo).forEach(threadToInfoEntry -> {
      Map<RecipientId, List<MarkedMessageInfo>> recipientIdToInfo = Stream.of(threadToInfoEntry.getValue())
                                                                          .map(info -> info)
                                                                          .collect(Collectors.groupingBy(info -> info.getSyncMessageId().getRecipientId()));

      Stream.of(recipientIdToInfo).forEach(entry -> {
        long                    threadId    = threadToInfoEntry.getKey();
        RecipientId             recipientId = entry.getKey();
        List<MarkedMessageInfo> infos       = entry.getValue();

        SendReadReceiptJob.enqueue(threadId, recipientId, infos);
      });
    });
  }

  public static void processCallEvents(@NonNull List<ConversationId> threads, long timestamp) {
    List<RecipientId> peers = SignalDatabase.threads().getRecipientIdsForThreadIds(threads.stream()
                                                                                          .filter(it -> it.getGroupStoryId() == null)
                                                                                          .map(ConversationId::getThreadId)
                                                                                          .collect(java.util.stream.Collectors.toList()));

    for (RecipientId peer : peers) {
      CallTable.Call lastCallInThread = SignalDatabase.calls().markAllCallEventsWithPeerBeforeTimestampRead(peer, timestamp);
      if (lastCallInThread != null) {
        AppDependencies.getJobManager().add(CallLogEventSendJob.forMarkedAsReadInConversation(lastCallInThread));
      }
    }
  }

  private static void scheduleDeletion(@NonNull List<ExpirationInfo> expirationInfo) {
    if (expirationInfo.size() > 0) {
      long now = System.currentTimeMillis();
      SignalDatabase.messages().markExpireStarted(Stream.of(expirationInfo).map(info -> new kotlin.Pair<>(info.getId(), now)).toList());

      AppDependencies.getExpiringMessageManager()
                     .scheduleDeletion(Stream.of(expirationInfo).map(info -> info.copy(info.getId(), info.getExpiresIn(), now, info.isMms())).toList());
    }
  }
}
