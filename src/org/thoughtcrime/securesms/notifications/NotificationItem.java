package org.thoughtcrime.securesms.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RoutingActivity;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.notifications.MessageNotifier.NotificationStateChangeListener;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;
import org.thoughtcrime.securesms.util.Util;

public class NotificationItem {

  private final Recipients   recipients;
  private final Recipient    individualRecipient;
  private final Recipients   threadRecipients;
  private final long         threadId;
  private       CharSequence text;
  private final Context      context;
  private final NotificationStateChangeListener listener;

  public NotificationItem(Recipient individualRecipient, Recipients recipients,
                          Recipients threadRecipients, long threadId, CharSequence text,
                          Context context, ListenableFutureTask<SlideDeck> slideDeckFuture,
                          NotificationStateChangeListener listener)
  {
    this.individualRecipient = individualRecipient;
    this.recipients          = recipients;
    this.threadRecipients    = threadRecipients;
    this.text                = text;
    this.threadId            = threadId;
    this.context             = context;
    this.listener            = listener;

    if (slideDeckFuture != null) {
      slideDeckFuture.addListener(new SlideDeckListener());
    }
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public String getIndividualRecipientName() {
    return individualRecipient.toShortString();
  }

  public CharSequence getText() {
    return text;
  }

  private void setText(int resId, Object... formatArgs) {
    if (formatArgs != null) {
      this.text = context.getString(resId, formatArgs);
    } else {
      this.text = context.getString(resId);
    }

    listener.onNotificationStateChanged();
  }

  public long getThreadId() {
    return threadId;
  }

  public CharSequence getBigStyleSummary() {
    return (text == null) ? "" : text;
  }

  public CharSequence getTickerText() {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(Util.getBoldedString(getIndividualRecipientName()));
    builder.append(": ");
    builder.append(getText());

    return builder;
  }

  public PendingIntent getPendingIntent(Context context) {
    Intent intent = new Intent(context, RoutingActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

    if (recipients != null || threadRecipients != null) {
      if (threadRecipients != null) intent.putExtra("recipients", threadRecipients.getIds());
      else                          intent.putExtra("recipients", recipients.getIds());

      intent.putExtra("thread_id", threadId);
    }

    intent.setData((Uri.parse("custom://"+System.currentTimeMillis())));

    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private class SlideDeckListener implements FutureTaskListener<SlideDeck> {
    @Override
    public void onSuccess(SlideDeck slideDeck) {
      if (NotificationItem.this.getText().length() > 0) {
        return;
      }

      for (Slide slide : slideDeck.getSlides()) {
        if (slide.hasImage()) {
          NotificationItem.this.setText(R.string.DraftDatabase_Draft_image_snippet);
          return;
        } else if (slide.hasAudio()) {
          NotificationItem.this.setText(R.string.DraftDatabase_Draft_audio_snippet);
          return;
        } else if (slide.hasVideo()) {
          NotificationItem.this.setText(R.string.DraftDatabase_Draft_video_snippet);
          return;
        }
      }
    }

    @Override
    public void onFailure(Throwable error) { }
  }
}
