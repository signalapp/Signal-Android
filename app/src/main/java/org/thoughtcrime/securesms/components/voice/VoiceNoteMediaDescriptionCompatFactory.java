package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
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
 * Factory responsible for building out MediaDescriptionCompat objects for voice notes.
 */
class VoiceNoteMediaDescriptionCompatFactory {

  public static final String EXTRA_MESSAGE_POSITION        = "voice.note.extra.MESSAGE_POSITION";
  public static final String EXTRA_THREAD_RECIPIENT_ID     = "voice.note.extra.RECIPIENT_ID";
  public static final String EXTRA_AVATAR_RECIPIENT_ID     = "voice.note.extra.AVATAR_ID";
  public static final String EXTRA_INDIVIDUAL_RECIPIENT_ID = "voice.note.extras.INDIVIDUAL_ID";
  public static final String EXTRA_THREAD_ID               = "voice.note.extra.THREAD_ID";
  public static final String EXTRA_COLOR                   = "voice.note.extra.COLOR";
  public static final String EXTRA_MESSAGE_ID              = "voice.note.extra.MESSAGE_ID";
  public static final String EXTRA_MESSAGE_TIMESTAMP       = "voice.note.extra.MESSAGE_TIMESTAMP";

  private static final String TAG = Log.tag(VoiceNoteMediaDescriptionCompatFactory.class);

  private VoiceNoteMediaDescriptionCompatFactory() {}

  static MediaDescriptionCompat buildMediaDescription(@NonNull Context context,
                                                      long threadId,
                                                      @NonNull Uri draftUri)
  {

    Recipient threadRecipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);
    if (threadRecipient == null) {
      threadRecipient = Recipient.UNKNOWN;
    }

    return buildMediaDescription(context,
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
   * Build out a MediaDescriptionCompat for a given voice note. Expects to be run
   * on a background thread.
   *
   * @param context       Context.
   * @param messageRecord The MessageRecord of the given voice note.
   * @return A MediaDescriptionCompat with all the details the service expects.
   */
  @WorkerThread
  @Nullable static MediaDescriptionCompat buildMediaDescription(@NonNull Context context,
                                                                @NonNull MessageRecord messageRecord)
  {
    int startingPosition = DatabaseFactory.getMmsSmsDatabase(context)
                                          .getMessagePositionInConversation(messageRecord.getThreadId(),
                                                                            messageRecord.getDateReceived());

    Recipient threadRecipient = Objects.requireNonNull(DatabaseFactory.getThreadDatabase(context)
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

    return buildMediaDescription(context,
                                 threadRecipient,
                                 avatarRecipient,
                                 sender,
                                 startingPosition,
                                 messageRecord.getThreadId(),
                                 messageRecord.getId(),
                                 messageRecord.getDateReceived(),
                                 uri);
  }

  private static MediaDescriptionCompat buildMediaDescription(@NonNull Context context,
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
      subtitle = context.getString(R.string.VoiceNoteMediaDescriptionCompatFactory__voice_message,
                                   DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(),
                                                                        dateReceived));
    }

    return new MediaDescriptionCompat.Builder()
                                     .setMediaUri(audioUri)
                                     .setTitle(title)
                                     .setSubtitle(subtitle)
                                     .setExtras(extras)
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
      return context.getString(R.string.VoiceNoteMediaDescriptionCompatFactory__s_to_s,
                               sender.getDisplayName(context),
                               threadRecipient.getDisplayName(context));
    } else if (preference.isDisplayContact()) {
      return sender.getDisplayName(context);
    } else {
      return context.getString(R.string.MessageNotifier_signal_message);
    }
  }
}
