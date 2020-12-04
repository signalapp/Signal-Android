package org.thoughtcrime.securesms.components.voice;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.Objects;

class VoiceNoteNotificationManager {

  private static final short NOW_PLAYING_NOTIFICATION_ID = 32221;

  private final Context                   context;
  private final MediaControllerCompat     controller;
  private final PlayerNotificationManager notificationManager;

  VoiceNoteNotificationManager(@NonNull Context context,
                               @NonNull MediaSessionCompat.Token token,
                               @NonNull PlayerNotificationManager.NotificationListener listener,
                               @NonNull VoiceNoteQueueDataAdapter dataAdapter)
  {
    this.context  = context;

    try {
      controller = new MediaControllerCompat(context, token);
    } catch (RemoteException e) {
      throw new IllegalArgumentException("Could not create a controller with given token");
    }

    notificationManager = PlayerNotificationManager.createWithNotificationChannel(context,
                                                                                  NotificationChannels.VOICE_NOTES,
                                                                                  R.string.NotificationChannel_voice_notes,
                                                                                  NOW_PLAYING_NOTIFICATION_ID,
                                                                                  new DescriptionAdapter());

    notificationManager.setMediaSessionToken(token);
    notificationManager.setSmallIcon(R.drawable.ic_notification);
    notificationManager.setRewindIncrementMs(0);
    notificationManager.setFastForwardIncrementMs(0);
    notificationManager.setNotificationListener(listener);
    notificationManager.setColorized(true);
    notificationManager.setControlDispatcher(new VoiceNoteNotificationControlDispatcher(dataAdapter));
  }

  public void hideNotification() {
    notificationManager.setPlayer(null);
  }

  public void showNotification(@NonNull Player player) {
    notificationManager.setPlayer(player);
  }

  private final class DescriptionAdapter implements PlayerNotificationManager.MediaDescriptionAdapter {

    private RecipientId cachedRecipientId;
    private Bitmap      cachedBitmap;

    @Override
    public String getCurrentContentTitle(Player player) {
      if (hasMetadata()) {
        return Objects.toString(controller.getMetadata().getDescription().getTitle(), null);
      } else {
        return null;
      }
    }

    @Override
    public @Nullable PendingIntent createCurrentContentIntent(Player player) {
      if (!hasMetadata()) return null;

      String serializedRecipientId = controller.getMetadata().getString(VoiceNoteMediaDescriptionCompatFactory.EXTRA_THREAD_RECIPIENT_ID);
      if (serializedRecipientId == null) {
        return null;
      }

      RecipientId recipientId      = RecipientId.from(serializedRecipientId);
      int         startingPosition = (int) controller.getMetadata().getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_MESSAGE_POSITION);
      long        threadId         = controller.getMetadata().getLong(VoiceNoteMediaDescriptionCompatFactory.EXTRA_THREAD_ID);

      MaterialColor color;
      try {
        color = MaterialColor.fromSerialized(controller.getMetadata().getString(VoiceNoteMediaDescriptionCompatFactory.EXTRA_COLOR));
      } catch (MaterialColor.UnknownColorException e) {
        color = ContactColors.UNKNOWN_COLOR;
      }

      notificationManager.setColor(color.toNotificationColor(context));

      Intent conversationActivity = ConversationIntents.createBuilder(context, recipientId, threadId)
                                                       .withStartingPosition(startingPosition)
                                                       .build();

      conversationActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      return PendingIntent.getActivity(context,
                                       0,
                                       conversationActivity,
                                       PendingIntent.FLAG_CANCEL_CURRENT);
    }

    @Override
    public String getCurrentContentText(Player player) {
      if (hasMetadata()) {
        return Objects.toString(controller.getMetadata().getDescription().getSubtitle(), null);
      } else {
        return null;
      }
    }

    @Override
    public @Nullable Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
      if (!hasMetadata() || !TextSecurePreferences.getNotificationPrivacy(context).isDisplayContact()) {
        cachedBitmap      = null;
        cachedRecipientId = null;
        return null;
      }

      String serializedRecipientId = controller.getMetadata().getString(VoiceNoteMediaDescriptionCompatFactory.EXTRA_AVATAR_RECIPIENT_ID);
      if (serializedRecipientId == null) {
        return null;
      }

      RecipientId currentRecipientId = RecipientId.from(serializedRecipientId);

      if (Objects.equals(currentRecipientId, cachedRecipientId) && cachedBitmap != null) {
        return cachedBitmap;
      } else {
        cachedRecipientId = currentRecipientId;
        SignalExecutors.BOUNDED.execute(() -> {
          try {
            cachedBitmap = AvatarUtil.getBitmapForNotification(context, Recipient.resolved(cachedRecipientId));
            callback.onBitmap(cachedBitmap);
          } catch (Exception e) {
            cachedBitmap = null;
          }
        });

        return null;
      }
    }

    private boolean hasMetadata() {
      return controller.getMetadata() != null && controller.getMetadata().getDescription() != null;
    }
  }
}
