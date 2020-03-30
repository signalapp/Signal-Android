package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;

public class NotificationItem {

            private final long         id;
            private final boolean      mms;
  @NonNull  private final Recipient    conversationRecipient;
  @NonNull  private final Recipient    individualRecipient;
  @Nullable private final Recipient    threadRecipient;
            private final long         threadId;
  @Nullable private final CharSequence text;
            private final long         notificationTimestamp;
            private final long         messageReceivedTimestamp;
  @Nullable private final SlideDeck    slideDeck;
            private final boolean      jumpToMessage;

  public NotificationItem(long id,
                          boolean mms,
                          @NonNull Recipient individualRecipient,
                          @NonNull Recipient conversationRecipient,
                          @Nullable Recipient threadRecipient,
                          long threadId,
                          @Nullable CharSequence text,
                          long notificationTimestamp,
                          long messageReceivedTimestamp,
                          @Nullable SlideDeck slideDeck,
                          boolean jumpToMessage)
  {
    this.id                       = id;
    this.mms                      = mms;
    this.individualRecipient      = individualRecipient;
    this.conversationRecipient    = conversationRecipient;
    this.threadRecipient          = threadRecipient;
    this.text                     = text;
    this.threadId                 = threadId;
    this.notificationTimestamp    = notificationTimestamp;
    this.messageReceivedTimestamp = messageReceivedTimestamp;
    this.slideDeck                = slideDeck;
    this.jumpToMessage            = jumpToMessage;
  }

  public @NonNull  Recipient getRecipient() {
    return threadRecipient == null ? conversationRecipient : threadRecipient;
  }

  public @NonNull  Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public @Nullable CharSequence getText() {
    return text;
  }

  public long getTimestamp() {
    return notificationTimestamp;
  }

  public long getThreadId() {
    return threadId;
  }

  public @Nullable SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public PendingIntent getPendingIntent(Context context) {
    Recipient recipient        = threadRecipient != null ? threadRecipient : conversationRecipient;
    int       startingPosition = jumpToMessage ? getStartingPosition(context, threadId, messageReceivedTimestamp) : -1;
    Intent    intent           = ConversationActivity.buildIntent(context, recipient.getId(), threadId, 0, -1, startingPosition);

    makeIntentUniqueToPreventMerging(intent);

    return TaskStackBuilder.create(context)
                           .addNextIntentWithParentStack(intent)
                           .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  public long getId() {
    return id;
  }

  public boolean isMms() {
    return mms;
  }

  private static int getStartingPosition(@NonNull Context context, long threadId, long receivedTimestampMs) {
    return DatabaseFactory.getMmsSmsDatabase(context).getMessagePositionInConversation(threadId, receivedTimestampMs);
  }

  private static void makeIntentUniqueToPreventMerging(@NonNull Intent intent) {
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));
  }
}
