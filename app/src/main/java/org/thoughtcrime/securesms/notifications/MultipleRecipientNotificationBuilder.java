package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import android.text.SpannableStringBuilder;
import org.session.libsession.messaging.contacts.Contact;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.loki.activities.HomeActivity;
import org.thoughtcrime.securesms.loki.database.SessionContactDatabase;
import org.session.libsession.utilities.NotificationPrivacyPreference;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import java.util.LinkedList;
import java.util.List;

import network.loki.messenger.R;

public class MultipleRecipientNotificationBuilder extends AbstractNotificationBuilder {

  private final List<CharSequence> messageBodies = new LinkedList<>();

  public MultipleRecipientNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    setColor(context.getResources().getColor(R.color.textsecure_primary));
    setSmallIcon(R.drawable.ic_notification);
    setContentTitle(context.getString(R.string.app_name));
    setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, HomeActivity.class), 0));
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

  public void setMostRecentSender(Recipient recipient, Recipient threadRecipient) {
    String displayName = recipient.toShortString();
    if (threadRecipient.isOpenGroupRecipient()) {
      SessionContactDatabase contactDB = DatabaseFactory.getSessionContactDatabase(context);
      String sessionID = recipient.getAddress().serialize();
      Contact contact = contactDB.getContactWithSessionID(sessionID);
      if (contact != null) {
        displayName = contact.displayName(Contact.ContactContext.OPEN_GROUP);
      } else {
        displayName = sessionID;
      }
    }
    if (privacy.isDisplayContact()) {
      setContentText(context.getString(R.string.MessageNotifier_most_recent_from_s, displayName));
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

  public void addMessageBody(@NonNull Recipient sender, Recipient threadRecipient, @Nullable CharSequence body) {
    String displayName = sender.toShortString();
    if (threadRecipient.isOpenGroupRecipient()) {
      SessionContactDatabase contactDB = DatabaseFactory.getSessionContactDatabase(context);
      String sessionID = sender.getAddress().serialize();
      Contact contact = contactDB.getContactWithSessionID(sessionID);
      if (contact != null) {
        displayName = contact.displayName(Contact.ContactContext.OPEN_GROUP);
      } else {
        displayName = sessionID;
      }
    }
    if (privacy.isDisplayMessage()) {
      SpannableStringBuilder builder = new SpannableStringBuilder();
      builder.append(Util.getBoldedString(displayName));
      builder.append(": ");
      builder.append(body == null ? "" : body);

      messageBodies.add(builder);
    } else if (privacy.isDisplayContact()) {
      messageBodies.add(Util.getBoldedString(displayName));
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
