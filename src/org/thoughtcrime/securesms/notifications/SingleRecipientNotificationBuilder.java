package org.thoughtcrime.securesms.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Action;
import android.support.v4.app.RemoteInput;
import android.text.SpannableStringBuilder;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.preferences.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.BitmapUtil;

import java.util.LinkedList;
import java.util.List;

public class SingleRecipientNotificationBuilder extends AbstractNotificationBuilder {

  private final List<CharSequence> messageBodies = new LinkedList<>();

  public SingleRecipientNotificationBuilder(@NonNull Context context, @NonNull NotificationPrivacyPreference privacy) {
    super(context, privacy);

    setSmallIcon(R.drawable.icon_notification);
    setColor(context.getResources().getColor(R.color.textsecure_primary));
    setPriority(NotificationCompat.PRIORITY_HIGH);
    setCategory(NotificationCompat.CATEGORY_MESSAGE);
    setDeleteIntent(PendingIntent.getBroadcast(context, 0, new Intent(MessageNotifier.DeleteReceiver.DELETE_REMINDER_ACTION), 0));
  }

  public void setSender(@NonNull Recipient recipient) {
    if (privacy.isDisplayContact()) {
      setContentTitle(recipient.toShortString());

      if (recipient.getContactUri() != null) {
        addPerson(recipient.getContactUri().toString());
      }

      setLargeIcon(recipient.getContactPhoto()
                            .asDrawable(context, recipient.getColor()
                                                          .toConversationColor(context)));
    } else {
      setContentTitle(context.getString(R.string.SingleRecipientNotificationBuilder_new_textsecure_message));
      setLargeIcon(Recipient.getUnknownRecipient()
                            .getContactPhoto()
                            .asDrawable(context, Recipient.getUnknownRecipient()
                                                          .getColor()
                                                          .toConversationColor(context)));
    }
  }

  public void setMessageCount(int messageCount) {
    setContentInfo(String.valueOf(messageCount));
    setNumber(messageCount);
  }

  public void setPrimaryMessageBody(CharSequence message) {
    if (privacy.isDisplayMessage()) {
      setContentText(message);
    } else {
      setContentText(context.getString(R.string.SingleRecipientNotificationBuilder_contents_hidden));
    }
  }

  public void addActions(@Nullable MasterSecret masterSecret,
                         @NonNull PendingIntent markReadIntent,
                         @NonNull PendingIntent quickReplyIntent,
                         @NonNull PendingIntent wearableReplyIntent)
  {
    Action markAsReadAction = new Action(R.drawable.check,
                                         context.getString(R.string.MessageNotifier_mark_read),
                                         markReadIntent);

    if (masterSecret != null) {
      Action replyAction = new Action(R.drawable.ic_reply_white_36dp,
                                      context.getString(R.string.MessageNotifier_reply),
                                      quickReplyIntent);

      Action wearableReplyAction = new Action.Builder(R.drawable.ic_reply,
                                                      context.getString(R.string.MessageNotifier_reply),
                                                      wearableReplyIntent)
          .addRemoteInput(new RemoteInput.Builder(MessageNotifier.EXTRA_VOICE_REPLY)
                              .setLabel(context.getString(R.string.MessageNotifier_reply)).build())
          .build();

      addAction(markAsReadAction);
      addAction(replyAction);

      extend(new NotificationCompat.WearableExtender().addAction(markAsReadAction)
                                                      .addAction(wearableReplyAction));
    } else {
      addAction(markAsReadAction);

      extend(new NotificationCompat.WearableExtender().addAction(markAsReadAction));
    }
  }

  public void addMessageBody(@Nullable CharSequence messageBody) {
    if (privacy.isDisplayMessage()) {
      messageBodies.add(messageBody == null ? "" : messageBody);
    }
  }

  public void setTicker(@NonNull Recipient recipient, @Nullable CharSequence message) {
    if (privacy.isDisplayMessage()) {
      setTicker(getStyledMessage(recipient, message));
    } else if (privacy.isDisplayContact()) {
      setTicker(getStyledMessage(recipient, context.getString(R.string.SingleRecipientNotificationBuilder_new_textsecure_message)));
    } else {
      setTicker(context.getString(R.string.SingleRecipientNotificationBuilder_new_textsecure_message));
    }
  }

  @Override
  public Notification build() {
    if (privacy.isDisplayMessage()) {
      SpannableStringBuilder content = new SpannableStringBuilder();

      for (CharSequence message : messageBodies) {
        content.append(message);
        content.append('\n');
      }

      setStyle(new NotificationCompat.BigTextStyle().bigText(content));
    }

    return super.build();
  }

  private void setLargeIcon(@Nullable Drawable drawable) {
    if (drawable != null) {
      int    largeIconTargetSize  = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);
      Bitmap recipientPhotoBitmap = BitmapUtil.createFromDrawable(drawable, largeIconTargetSize, largeIconTargetSize);

      if (recipientPhotoBitmap != null) {
        setLargeIcon(recipientPhotoBitmap);
      }
    }
  }

}
