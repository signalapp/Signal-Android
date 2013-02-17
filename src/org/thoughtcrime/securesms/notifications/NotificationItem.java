package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;

import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;

public class NotificationItem {

  private final Recipients   recipients;
  private final long         threadId;
  private final CharSequence text;
  private final Uri          image;

  public NotificationItem(Recipients recipients, long threadId, CharSequence text, Uri image) {
    this.recipients = recipients;
    this.text       = text;
    this.image      = image;
    this.threadId   = threadId;
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public String getRecipientName() {
    return recipients.getPrimaryRecipient().toShortString();
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
    return (text == null) ? "" : text;
  }

  public CharSequence getTickerText() {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(Util.getBoldedString(getRecipientName()));
    builder.append(": ");
    builder.append(getText());

    return builder;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent intent = new Intent(context, RoutingActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    if (recipients.getPrimaryRecipient() != null) {
      intent.putExtra("recipients", recipients);
      intent.putExtra("thread_id", threadId);
    }

    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, 0);
  }

}
