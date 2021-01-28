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
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase.ExpirationInfo;
import org.thoughtcrime.securesms.database.MessageDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MessageDatabase.SyncMessageId;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MarkReadReceiver extends BroadcastReceiver {

  private static final String TAG                   = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION          = "org.thoughtcrime.securesms.notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA      = "thread_ids";
  public static final  String NOTIFICATION_ID_EXTRA = "notification_id";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      NotificationCancellationHelper.cancelLegacy(context, intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1));

      SignalExecutors.BOUNDED.execute(() -> {
        List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();

        for (long threadId : threadIds) {
          Log.i(TAG, "Marking as read: " + threadId);
          List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
          messageIdsCollection.addAll(messageIds);
        }

        process(context, messageIdsCollection);

        ApplicationDependencies.getMessageNotifier().updateNotification(context);
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
      Map<RecipientId, List<SyncMessageId>> idMapForThread = Stream.of(threadToInfoEntry.getValue())
                                                                   .map(MarkedMessageInfo::getSyncMessageId)
                                                                   .collect(Collectors.groupingBy(SyncMessageId::getRecipientId));

      Stream.of(idMapForThread).forEach(entry -> {
        List<Long> timestamps = Stream.of(entry.getValue()).map(SyncMessageId::getTimetamp).toList();

        SendReadReceiptJob.enqueue(threadToInfoEntry.getKey(), entry.getKey(), timestamps);
      });
    });
  }

  private static void scheduleDeletion(@NonNull Context context,
                                       @NonNull List<ExpirationInfo> smsExpirationInfo,
                                       @NonNull List<ExpirationInfo> mmsExpirationInfo)
  {
    if (smsExpirationInfo.size() > 0) {
      DatabaseFactory.getSmsDatabase(context).markExpireStarted(Stream.of(smsExpirationInfo).map(ExpirationInfo::getId).toList(), System.currentTimeMillis());
    }

    if (mmsExpirationInfo.size() > 0) {
      DatabaseFactory.getMmsDatabase(context).markExpireStarted(Stream.of(mmsExpirationInfo).map(ExpirationInfo::getId).toList(), System.currentTimeMillis());
    }

    if (smsExpirationInfo.size() + mmsExpirationInfo.size() > 0) {
      ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      Stream.concat(Stream.of(smsExpirationInfo), Stream.of(mmsExpirationInfo))
            .forEach(info -> expirationManager.scheduleDeletion(info.getId(), info.isMms(), info.getExpiresIn()));
    }
  }
}
