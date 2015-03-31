package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;

public class NotificationItem {

  private final Recipients   recipients;
  private final Recipient    individualRecipient;
  private final Recipients   threadRecipients;
  private final long         threadId;
  private final CharSequence text;
  private final Uri          image;
  private final long         timestamp;

  public NotificationItem(Recipient individualRecipient, Recipients recipients,
                          Recipients threadRecipients, long threadId,
                          CharSequence text, Uri image, long timestamp)
  {
    this.individualRecipient = individualRecipient;
    this.recipients          = recipients;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.image               = image;
    this.threadId            = threadId;
    this.timestamp           = timestamp;
  }

  private Recipient getGroupRecipient() {
    if (threadRecipients != null && threadRecipients.isGroupRecipient()) {
      return threadRecipients.getPrimaryRecipient();
    } else {
      return null;
    }
  }

  private boolean hasGroupRecipient() {
    return getGroupRecipient() != null;
  }

  public Recipient getPrimaryRecipient() {
    if (hasGroupRecipient()) return getGroupRecipient();
    else                     return individualRecipient;
  }

  public CharSequence getText() {
    return text;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Uri getImage() {
    return image;
  }

  public boolean hasImage() {
    return image != null;
  }

  public long getThreadId() {
    return threadId;
  }

  public CharSequence getSingleThreadSummary() {
    SpannableStringBuilder bigSummary = new SpannableStringBuilder();

    if (hasGroupRecipient()) {
      bigSummary.append(Util.getBoldedString(individualRecipient.toShortString() + ": "));
    }

    return bigSummary.append(getText());
  }

  public CharSequence getTickerText() {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    if (hasGroupRecipient()) {
      builder.append(Util.getBoldedString(getGroupRecipient().toShortString()));
    } else {
      builder.append(Util.getBoldedString(individualRecipient.toShortString()));
    }
    builder.append(": ");
    builder.append(getText());

    return builder;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent intent = new Intent(context, ConversationActivity.class);

    Recipients notifyRecipients = threadRecipients != null ? threadRecipients : recipients;
    if (notifyRecipients != null) intent.putExtra("recipients", notifyRecipients.getIds());

    intent.putExtra("thread_id", threadId);
    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

}
