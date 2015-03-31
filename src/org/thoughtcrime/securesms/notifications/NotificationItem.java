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

  public NotificationItem(Recipient individualRecipient, Recipients recipients,
                          Recipients threadRecipients, long threadId,
                          CharSequence text, Uri image)
  {
    this.individualRecipient = individualRecipient;
    this.recipients          = recipients;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.image               = image;
    this.threadId            = threadId;
  }

  private Recipient getGroupRecipient() {
    if (threadRecipients != null && threadRecipients.isGroupRecipient()) {
      return threadRecipients.getPrimaryRecipient();
    } else {
      return null;
    }
  }

  public Recipient getPrimaryRecipient() {
    if  (getGroupRecipient() != null) return getGroupRecipient();
    else                              return individualRecipient;
  }

  public CharSequence getText() {
    return text;
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

  public CharSequence getBigStyleSummary() {
    SpannableStringBuilder bigSummary = new SpannableStringBuilder();

    if (getGroupRecipient() != null) {
      bigSummary.append(Util.getBoldedString(individualRecipient.toShortString() + ": "));
    }

    return (text == null) ? bigSummary : bigSummary.append(text);
  }

  public CharSequence getTickerText() {
    SpannableStringBuilder builder = new SpannableStringBuilder();

    if (getGroupRecipient() != null) {
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
