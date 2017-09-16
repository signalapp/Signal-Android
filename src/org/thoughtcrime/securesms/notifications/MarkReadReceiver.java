package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.ExpirationInfo;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.jobs.MultiDeviceReadUpdateJob;
import org.thoughtcrime.securesms.jobs.SendReadReceiptJob;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MarkReadReceiver extends MasterSecretBroadcastReceiver {

  private static final String TAG                   = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION          = "org.thoughtcrime.securesms.notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA      = "thread_ids";
  public static final  String NOTIFICATION_ID_EXTRA = "notification_id";

  @Override
  protected void onReceive(final Context context, Intent intent, @Nullable final MasterSecret masterSecret)
  {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      NotificationManagerCompat.from(context).cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1));

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();

          for (long threadId : threadIds) {
            Log.w(TAG, "Marking as read: " + threadId);
            List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
            messageIdsCollection.addAll(messageIds);
          }

          process(context, messageIdsCollection);

          MessageNotifier.updateNotification(context, masterSecret);

          return null;
        }
      }.execute();
    }
  }

  public static void process(@NonNull Context context, @NonNull List<MarkedMessageInfo> markedReadMessages) {
    if (markedReadMessages.isEmpty()) return;

    List<SyncMessageId> syncMessageIds = new LinkedList<>();

    for (MarkedMessageInfo messageInfo : markedReadMessages) {
      scheduleDeletion(context, messageInfo.getExpirationInfo());
      syncMessageIds.add(messageInfo.getSyncMessageId());
    }

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new MultiDeviceReadUpdateJob(context, syncMessageIds));

    Map<Address, List<SyncMessageId>> addressMap = Stream.of(markedReadMessages)
                                                         .map(MarkedMessageInfo::getSyncMessageId)
                                                         .collect(Collectors.groupingBy(SyncMessageId::getAddress));

    for (Address address : addressMap.keySet()) {
      List<Long> timestamps = Stream.of(addressMap.get(address)).map(SyncMessageId::getTimetamp).toList();

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new SendReadReceiptJob(context, address, timestamps));
    }
  }

  private static void scheduleDeletion(Context context, ExpirationInfo expirationInfo) {
    if (expirationInfo.getExpiresIn() > 0 && expirationInfo.getExpireStarted() <= 0) {
      ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      if (expirationInfo.isMms()) DatabaseFactory.getMmsDatabase(context).markExpireStarted(expirationInfo.getId());
      else                        DatabaseFactory.getSmsDatabase(context).markExpireStarted(expirationInfo.getId());

      expirationManager.scheduleDeletion(expirationInfo.getId(), expirationInfo.isMms(), expirationInfo.getExpiresIn());
    }
  }
}
