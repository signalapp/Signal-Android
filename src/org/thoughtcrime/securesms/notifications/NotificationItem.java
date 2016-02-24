package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.TaskStackBuilder;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

public class NotificationItem {

  private final @NonNull  Recipients        recipients;
  private final @NonNull  Recipient         individualRecipient;
  private final @Nullable Recipients        threadRecipients;
  private final long                        threadId;
  private final @Nullable CharSequence      text;
  private final long                        timestamp;
  private final @Nullable SlideDeck         slideDeck;
  private Boolean                           notified = false;

  public NotificationItem(@NonNull   Recipient individualRecipient,
                          @NonNull   Recipients recipients,
                          @Nullable  Recipients threadRecipients,
                          long threadId, @Nullable CharSequence text, long timestamp,
                          @Nullable SlideDeck slideDeck,
                          boolean notified)

  {
    this.individualRecipient = individualRecipient;
    this.recipients          = recipients;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.threadId            = threadId;
    this.timestamp           = timestamp;
    this.slideDeck           = slideDeck;
    this.notified            = notified;
  }

  public @NonNull  Recipients getRecipients() {
    return threadRecipients == null ? recipients : threadRecipients;
  }

  public @NonNull  Recipient getIndividualRecipient() {
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

  public boolean isNotified() {
    return this.notified;
  }

  public void setNotified(boolean notified) {
    this.notified = notified;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent     intent           = new Intent(context, ConversationActivity.class);
    Recipients notifyRecipients = threadRecipients != null ? threadRecipients : recipients;
    if (notifyRecipients != null) intent.putExtra("recipients", notifyRecipients.getIds());

    intent.putExtra("thread_id", threadId);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return TaskStackBuilder.create(context)
                           .addNextIntentWithParentStack(intent)
                           .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof NotificationItem)) {
      return false;
    }

    final NotificationItem other = (NotificationItem) obj;

    if (this.timestamp != other.timestamp) {
      return false;
    }

    if (this.threadId != other.threadId) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = 17;

    result = 31 * result + (int) (this.timestamp ^ (this.timestamp >>> 32));
    result = 31 * result + (int) (this.threadId ^ (this.threadId >>> 32));

    return result;
  }


}
