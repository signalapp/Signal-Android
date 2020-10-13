package org.thoughtcrime.securesms.components.voice;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.preferences.widgets.NotificationPrivacyPreference;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Factory responsible for building out MediaDescriptionCompat objects for voice notes.
 */
class VoiceNoteMediaDescriptionCompatFactory {

  public static final String EXTRA_MESSAGE_POSITION = "voice.note.extra.MESSAGE_POSITION";
  public static final String EXTRA_RECIPIENT_ID     = "voice.note.extra.RECIPIENT_ID";
  public static final String EXTRA_THREAD_ID        = "voice.note.extra.THREAD_ID";
  public static final String EXTRA_COLOR            = "voice.note.extra.COLOR";

  private static final String TAG = Log.tag(VoiceNoteMediaDescriptionCompatFactory.class);

  private VoiceNoteMediaDescriptionCompatFactory() {}

  /**
   * Build out a MediaDescriptionCompat for a given voice note. Expects to be run
   * on a background thread.
   *
   * @param context     Context.
   * @param uri         The AudioSlide Uri of the given voice note.
   * @param messageId   The Message ID of the given voice note.
   *
   * @return A MediaDescriptionCompat with all the details the service expects.
   */
  @WorkerThread
  static MediaDescriptionCompat buildMediaDescription(@NonNull Context context,
                                                      @NonNull Uri uri,
                                                      long messageId)
  {
    final MessageRecord messageRecord;
    try {
      messageRecord = DatabaseFactory.getMmsDatabase(context).getMessageRecord(messageId);
    } catch (NoSuchMessageException e) {
      Log.w(TAG, "buildMediaDescription: ", e);
      return null;
    }

    int startingPosition = DatabaseFactory.getMmsSmsDatabase(context)
                                          .getMessagePositionInConversation(messageRecord.getThreadId(),
                                                                            messageRecord.getDateReceived());

    Bundle extras = new Bundle();
    extras.putString(EXTRA_RECIPIENT_ID, messageRecord.getIndividualRecipient().getId().serialize());
    extras.putLong(EXTRA_MESSAGE_POSITION, startingPosition);
    extras.putLong(EXTRA_THREAD_ID, messageRecord.getThreadId());
    extras.putString(EXTRA_COLOR, messageRecord.getIndividualRecipient().getColor().serialize());

    NotificationPrivacyPreference preference = TextSecurePreferences.getNotificationPrivacy(context);

    String title;
    if (preference.isDisplayContact()) {
      title = messageRecord.getIndividualRecipient().getDisplayName(context);
    } else {
      title = context.getString(R.string.MessageNotifier_signal_message);
    }

    return new MediaDescriptionCompat.Builder()
                                     .setMediaUri(uri)
                                     .setTitle(title)
                                     .setSubtitle(context.getString(R.string.ThreadRecord_voice_message))
                                     .setExtras(extras)
                                     .build();
  }

}
