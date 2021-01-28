package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedList;
import java.util.List;

public class MultipleRecipientNotificationBuilder extends AbstractNotificationBuilder {

  private final List<CharSequence> messageBodies = new LinkedList<>();

  public MultipleRecipientNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    setColor(context.getResources().getColor(R.color.core_ultramarine));
    setSmallIcon(R.drawable.ic_notification);
    setContentTitle(context.getString(R.string.app_name));
    // TODO [greyson] Navigation
    setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), 0));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);
    setGroupSummary(true);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }

  public void setMessageCount(int messageCount, int threadCount) {
    setSubText(context.getString(R.string.MessageNotifier_d_new_messages_in_d_conversations,
                                 messageCount, threadCount));
    setContentInfo(String.valueOf(messageCount));
    setNumber(messageCount);
  }

  public void setMostRecentSender(Recipient recipient) {
    if (privacy.isDisplayContact()) {
      setContentText(context.getString(R.string.MessageNotifier_most_recent_from_s,
                                       recipient.getDisplayName(context)));
    }

    if (recipient.getNotificationChannel() != null) {
      setChannelId(recipient.getNotificationChannel());
    }
  }

  public void addActions(PendingIntent markAsReadIntent) {
    NotificationCompat.Action markAllAsReadAction = new NotificationCompat.Action(R.drawable.check,
                                            context.getString(R.string.MessageNotifier_mark_all_as_read),
                                            markAsReadIntent);
    addAction(markAllAsReadAction);
    extend(new NotificationCompat.WearableExtender().addAction(markAllAsReadAction));
  }

  public void addMessageBody(@NonNull Recipient sender, @Nullable CharSequence body) {
    if (privacy.isDisplayMessage()) {
      messageBodies.add(getStyledMessage(sender, body));
    } else if (privacy.isDisplayContact()) {
      messageBodies.add(Util.getBoldedString(sender.getDisplayName(context)));
    }

    if (privacy.isDisplayContact() && sender.getContactUri() != null) {
      addPerson(sender.getContactUri().toString());
    }
  }

  @Override
  public Notification build() {
    if (privacy.isDisplayMessage() || privacy.isDisplayContact()) {
      NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

      for (CharSequence body : messageBodies) {
        style.addLine(trimToDisplayLength(body));
      }

      setStyle(style);
    }

    return super.build();
  }
}
