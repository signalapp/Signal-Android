package org.thoughtcrime.securesms.components.voice;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;

import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.AvatarUtil;

import java.util.Objects;

class VoiceNoteNotificationManager {

  private static final short NOW_PLAYING_NOTIFICATION_ID = 32221;

  private final Context                   context;
  private final MediaControllerCompat     controller;
  private final PlayerNotificationManager notificationManager;

  VoiceNoteNotificationManager(@NonNull Context context,
                               @NonNull MediaSessionCompat.Token token,
                               @NonNull PlayerNotificationManager.NotificationListener listener)
  {
    this.context        = context;
    controller          = new MediaControllerCompat(context, token);
    notificationManager = new PlayerNotificationManager.Builder(context, NOW_PLAYING_NOTIFICATION_ID, NotificationChannels.getInstance().VOICE_NOTES)
                                                       .setChannelNameResourceId(R.string.NotificationChannel_voice_notes)
                                                       .setMediaDescriptionAdapter(new DescriptionAdapter())
                                                       .setNotificationListener(listener)
                                                       .build();

    notificationManager.setMediaSessionToken(token);
    notificationManager.setSmallIcon(R.drawable.ic_notification);
    notificationManager.setColorized(true);
    notificationManager.setUseFastForwardAction(false);
    notificationManager.setUseRewindAction(false);
    notificationManager.setUseStopAction(true);
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
      if (!hasMetadata()) {
        return null;
      }

      String serializedRecipientId = controller.getMetadata().getString(VoiceNoteMediaItemFactory.EXTRA_THREAD_RECIPIENT_ID);
      if (serializedRecipientId == null) {
        return null;
      }

      RecipientId recipientId      = RecipientId.from(serializedRecipientId);
      int         startingPosition = (int) controller.getMetadata().getLong(VoiceNoteMediaItemFactory.EXTRA_MESSAGE_POSITION);
      long        threadId         = controller.getMetadata().getLong(VoiceNoteMediaItemFactory.EXTRA_THREAD_ID);

      int color = (int) controller.getMetadata().getLong(VoiceNoteMediaItemFactory.EXTRA_COLOR);

      if (color == 0) {
        color = ChatColorsPalette.UNKNOWN_CONTACT.asSingleColor();
      }

      notificationManager.setColor(color);

      Intent conversationActivity = ConversationIntents.createBuilder(context, recipientId, threadId)
                                                       .withStartingPosition(startingPosition)
                                                       .build();

      conversationActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

      return PendingIntent.getActivity(context,
                                       0,
                                       conversationActivity,
                                       PendingIntentFlags.cancelCurrent());
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
      if (!hasMetadata() || !SignalStore.settings().getMessageNotificationsPrivacy().isDisplayContact()) {
        cachedBitmap      = null;
        cachedRecipientId = null;
        return null;
      }

      String serializedRecipientId = controller.getMetadata().getString(VoiceNoteMediaItemFactory.EXTRA_AVATAR_RECIPIENT_ID);
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
