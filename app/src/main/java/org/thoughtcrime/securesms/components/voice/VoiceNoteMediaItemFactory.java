package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * Factory responsible for building out MediaItem objects for voice notes.
 */
class VoiceNoteMediaItemFactory {

  public static final String EXTRA_MESSAGE_POSITION        = "voice.note.extra.MESSAGE_POSITION";
  public static final String EXTRA_THREAD_RECIPIENT_ID     = "voice.note.extra.RECIPIENT_ID";
  public static final String EXTRA_AVATAR_RECIPIENT_ID     = "voice.note.extra.AVATAR_ID";
  public static final String EXTRA_INDIVIDUAL_RECIPIENT_ID = "voice.note.extras.INDIVIDUAL_ID";
  public static final String EXTRA_THREAD_ID               = "voice.note.extra.THREAD_ID";
  public static final String EXTRA_COLOR                   = "voice.note.extra.COLOR";
  public static final String EXTRA_MESSAGE_ID              = "voice.note.extra.MESSAGE_ID";
  public static final String EXTRA_MESSAGE_TIMESTAMP       = "voice.note.extra.MESSAGE_TIMESTAMP";

  public static final Uri NEXT_URI = Uri.parse("file:///android_asset/sounds/state-change_confirm-down.ogg");
  public static final Uri END_URI  = Uri.parse("file:///android_asset/sounds/state-change_confirm-up.ogg");

  private static final String TAG = Log.tag(VoiceNoteMediaItemFactory.class);

  private VoiceNoteMediaItemFactory() {}

  static MediaItem buildMediaItem(@NonNull Context context,
                                  long threadId,
                                  @NonNull Uri draftUri)
  {

    Recipient threadRecipient = SignalDatabase.threads().getRecipientForThreadId(threadId);
    if (threadRecipient == null) {
      threadRecipient = Recipient.UNKNOWN;
    }

    return buildMediaItem(context,
                          threadRecipient,
                          Recipient.self(),
                          Recipient.self(),
                          0,
                          threadId,
                          -1,
                          System.currentTimeMillis(),
                          draftUri);
  }

  /**
   * Build out a MediaItem for a given voice note. Expects to be run
   * on a background thread.
   *
   * @param context       Context.
   * @param messageRecord The MessageRecord of the given voice note.
   * @return A MediaItem with all the details the service expects.
   */
  @WorkerThread
  @Nullable static MediaItem buildMediaItem(@NonNull Context context,
                                            @NonNull MessageRecord messageRecord)
  {
    int startingPosition = SignalDatabase.messages()
                                         .getMessagePositionInConversation(messageRecord.getThreadId(),
                                                                           messageRecord.getDateReceived());

    Recipient threadRecipient = Objects.requireNonNull(SignalDatabase.threads()
                                                                      .getRecipientForThreadId(messageRecord.getThreadId()));
    Recipient  sender          = messageRecord.isOutgoing() ? Recipient.self() : messageRecord.getIndividualRecipient();
    Recipient  avatarRecipient = threadRecipient.isGroup() ? threadRecipient : sender;
    AudioSlide audioSlide      = ((MmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide();

    if (audioSlide == null) {
      Log.w(TAG, "Message does not have an audio slide. Can't play this voice note.");
      return null;
    }

    Uri uri = audioSlide.getUri();
    if (uri == null) {
      Log.w(TAG, "Audio slide does not have a URI. Can't play this voice note.");
      return null;
    }

    return buildMediaItem(context,
                          threadRecipient,
                          avatarRecipient,
                          sender,
                          startingPosition,
                          messageRecord.getThreadId(),
                          messageRecord.getId(),
                          messageRecord.getDateReceived(),
                          uri);
  }

  private static MediaItem buildMediaItem(@NonNull Context context,
                                          @NonNull Recipient threadRecipient,
                                          @NonNull Recipient avatarRecipient,
                                          @NonNull Recipient sender,
                                          int startingPosition,
                                          long threadId,
                                          long messageId,
                                          long dateReceived,
                                          @NonNull Uri audioUri)
  {
    Bundle extras = new Bundle();
    extras.putString(EXTRA_THREAD_RECIPIENT_ID, threadRecipient.getId().serialize());
    extras.putString(EXTRA_AVATAR_RECIPIENT_ID, avatarRecipient.getId().serialize());
    extras.putString(EXTRA_INDIVIDUAL_RECIPIENT_ID, sender.getId().serialize());
    extras.putLong(EXTRA_MESSAGE_POSITION, startingPosition);
    extras.putLong(EXTRA_THREAD_ID, threadId);
    extras.putLong(EXTRA_COLOR, threadRecipient.getChatColors().asSingleColor());
    extras.putLong(EXTRA_MESSAGE_ID, messageId);
    extras.putLong(EXTRA_MESSAGE_TIMESTAMP, dateReceived);

    NotificationPrivacyPreference preference = SignalStore.settings().getMessageNotificationsPrivacy();

    String title = getTitle(context, sender, threadRecipient, preference);

    String subtitle = null;
    if (preference.isDisplayContact()) {
      subtitle = context.getString(R.string.VoiceNoteMediaItemFactory__voice_message,
                                   DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(),
                                                                        dateReceived));
    }

    return new MediaItem.Builder()
                        .setUri(audioUri)
                        .setMediaMetadata(
                            new MediaMetadata.Builder()
                                .setTitle(title)
                                .setSubtitle(subtitle)
                                .setExtras(extras)
                                .build()
                        )
                        .setTag(
                            new MediaDescriptionCompat.Builder()
                                .setMediaUri(audioUri)
                                .setTitle(title)
                                .setSubtitle(subtitle)
                                .setExtras(extras)
                                .build())
                        .build();
  }

  public static @NonNull String getTitle(@NonNull Context context, @NonNull Recipient sender, @NonNull Recipient threadRecipient, @Nullable NotificationPrivacyPreference notificationPrivacyPreference) {
    NotificationPrivacyPreference preference;
    if (notificationPrivacyPreference == null) {
      preference = new NotificationPrivacyPreference("all");
    } else {
      preference = notificationPrivacyPreference;
    }

    if (preference.isDisplayContact() && threadRecipient.isGroup()) {
      return context.getString(R.string.VoiceNoteMediaItemFactory__s_to_s,
                               sender.getDisplayName(context),
                               threadRecipient.getDisplayName(context));
    } else if (preference.isDisplayContact()) {
      return sender.isSelf() && threadRecipient.isSelf() ? context.getString(R.string.note_to_self)
                                                         : sender.getDisplayName(context);
    } else {
      return context.getString(R.string.MessageNotifier_signal_message);
    }
  }

  public static MediaItem buildNextVoiceNoteMediaItem(@NonNull MediaItem source) {
    return cloneMediaItem(source, "next", NEXT_URI);
  }

  public static MediaItem buildEndVoiceNoteMediaItem(@NonNull MediaItem source) {
    return cloneMediaItem(source, "end", END_URI);
  }

  private static MediaItem cloneMediaItem(MediaItem source, String mediaId, Uri uri) {
    MediaDescriptionCompat description = source.playbackProperties != null ? (MediaDescriptionCompat) source.playbackProperties.tag : null;
    return source.buildUpon()
                 .setMediaId(mediaId)
                 .setUri(uri)
                 .setTag(
                     description != null ?
                     new MediaDescriptionCompat.Builder()
                         .setMediaUri(uri)
                         .setTitle(description.getTitle())
                         .setSubtitle(description.getSubtitle())
                         .setExtras(description.getExtras())
                         .build() : null)
                 .build();
  }
}
