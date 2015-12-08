package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;

public class NotificationItem {

  private final Recipients                  recipients;
  private final Recipient                   individualRecipient;
  private final Recipients                  threadRecipients;
  private final long                        threadId;
  private final CharSequence                text;
  private final long                        timestamp;
  private final @Nullable SlideDeck         slideDeck;

  public NotificationItem(Recipient individualRecipient, Recipients recipients,
                          Recipients threadRecipients, long threadId,
                          CharSequence text, long timestamp,
                          @Nullable SlideDeck slideDeck)
  {
    this.individualRecipient = individualRecipient;
    this.recipients          = recipients;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.threadId            = threadId;
    this.timestamp           = timestamp;
    this.slideDeck           = slideDeck;
  }

  public Recipients getRecipients() {
    return threadRecipients == null ? recipients : threadRecipients;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public CharSequence getText() {
    return text;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getThreadId() {
    return threadId;
  }

  public @Nullable SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent     intent           = new Intent(context, ConversationActivity.class);
    Recipients notifyRecipients = threadRecipients != null ? threadRecipients : recipients;
    if (notifyRecipients != null) intent.putExtra("recipients", notifyRecipients.getIds());

    intent.putExtra("thread_id", threadId);
    intent.setData((Uri.parse("custom://" + System.currentTimeMillis())));

    if(Build.VERSION.SDK_INT >= 16) {
      PendingIntent pendingIntent = TaskStackBuilder.create(context)
                                                    .addParentStack(ConversationActivity.class)
                                                    .addNextIntent(intent)
                                                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
      return pendingIntent;
    }

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }


}
