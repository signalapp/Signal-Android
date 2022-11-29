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
import org.thoughtcrime.securesms.database.MessageTable.ExpirationInfo;
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MessageTable.SyncMessageId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;

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
      MessageNotifier notifier = ApplicationDependencies.getMessageNotifier();
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

        process(context, messageIdsCollection);

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
        finisher.finish();
      });
    }
  }

  public static void process(@NonNull Context context, @NonNull List<MarkedMessageInfo> markedReadMessages) {
    if (markedReadMessages.isEmpty()) return;

    List<SyncMessageId>  syncMessageIds    = Stream.of(markedReadMessages)
                                                   .map(MarkedMessageInfo::getSyncMessageId)
                                                   .toList();
    List<ExpirationInfo> mmsExpirationInfo = Stream.of(markedReadMessages)
                                                   .map(MarkedMessageInfo::getExpirationInfo)
                                                   .filter(ExpirationInfo::isMms)
                                                   .filter(info -> info.getExpiresIn() > 0 && info.getExpireStarted() <= 0)
                                                   .toList();
    List<ExpirationInfo> smsExpirationInfo = Stream.of(markedReadMessages)
                                                   .map(MarkedMessageInfo::getExpirationInfo)
                                                   .filterNot(ExpirationInfo::isMms)
                                                   .filter(info -> info.getExpiresIn() > 0 && info.getExpireStarted() <= 0)
                                                   .toList();

    scheduleDeletion(context, smsExpirationInfo, mmsExpirationInfo);

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

  private static void scheduleDeletion(@NonNull Context context,
                                       @NonNull List<ExpirationInfo> smsExpirationInfo,
                                       @NonNull List<ExpirationInfo> mmsExpirationInfo)
  {
    if (smsExpirationInfo.size() > 0) {
      SignalDatabase.sms().markExpireStarted(Stream.of(smsExpirationInfo).map(ExpirationInfo::getId).toList(), System.currentTimeMillis());
    }

    if (mmsExpirationInfo.size() > 0) {
      SignalDatabase.mms().markExpireStarted(Stream.of(mmsExpirationInfo).map(ExpirationInfo::getId).toList(), System.currentTimeMillis());
    }

    if (smsExpirationInfo.size() + mmsExpirationInfo.size() > 0) {
      ExpiringMessageManager expirationManager = ApplicationDependencies.getExpiringMessageManager();

      Stream.concat(Stream.of(smsExpirationInfo), Stream.of(mmsExpirationInfo))
            .forEach(info -> expirationManager.scheduleDeletion(info.getId(), info.isMms(), info.getExpiresIn()));
    }
  }
}
