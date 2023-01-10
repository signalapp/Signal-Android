package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.Objects;

public final class ThreadBodyUtil {

  private static final String TAG = Log.tag(ThreadBodyUtil.class);

  private ThreadBodyUtil() {
  }

  public static @NonNull String getFormattedBodyFor(@NonNull Context context, @NonNull MessageRecord record) {
    if (record.isMms()) {
      return getFormattedBodyForMms(context, (MmsMessageRecord) record);
    }

    return record.getBody();
  }

  private static @NonNull String getFormattedBodyForMms(@NonNull Context context, @NonNull MmsMessageRecord record) {
    if (record.getSharedContacts().size() > 0) {
      Contact contact = record.getSharedContacts().get(0);

      return ContactUtil.getStringSummary(context, contact).toString();
    } else if (record.getSlideDeck().getDocumentSlide() != null) {
      return format(context, record, EmojiStrings.FILE, R.string.ThreadRecord_file);
    } else if (record.getSlideDeck().getAudioSlide() != null) {
      return format(context, record, EmojiStrings.AUDIO, R.string.ThreadRecord_voice_message);
    } else if (MessageRecordUtil.hasSticker(record)) {
      String emoji = getStickerEmoji(record);
      return format(context, record, emoji, R.string.ThreadRecord_sticker);
    } else if (MessageRecordUtil.hasGiftBadge(record)) {
      return String.format("%s %s", EmojiStrings.GIFT, getGiftSummary(context, record));
    } else if (MessageRecordUtil.isStoryReaction(record)) {
      return getStoryReactionSummary(context, record);
    } else if (record.isPaymentNotification()) {
      return String.format("%s %s", EmojiStrings.CARD, context.getString(R.string.ThreadRecord_payment));
    } else if (record.isPaymentsRequestToActivate()) {
      return String.format("%s %s", EmojiStrings.CARD, getPaymentActivationRequestSummary(context, record));
    } else if (record.isPaymentsActivated()) {
      return String.format("%s %s", EmojiStrings.CARD, getPaymentActivatedSummary(context, record));
    } else if (record.isCallLog() && !record.isGroupCall()) {
      return getCallLogSummary(context, record);
    }

    boolean hasImage = false;
    boolean hasVideo = false;
    boolean hasGif   = false;

    for (Slide slide : record.getSlideDeck().getSlides()) {
      hasVideo |= slide.hasVideo();
      hasImage |= slide.hasImage();
      hasGif   |= slide instanceof GifSlide || slide.isVideoGif();
    }

    if (hasGif) {
      return format(context, record, EmojiStrings.GIF, R.string.ThreadRecord_gif);
    } else if (hasVideo) {
      return format(context, record, EmojiStrings.VIDEO, R.string.ThreadRecord_video);
    } else if (hasImage) {
      return format(context, record, EmojiStrings.PHOTO, R.string.ThreadRecord_photo);
    } else if (TextUtils.isEmpty(record.getBody())) {
      return context.getString(R.string.ThreadRecord_media_message);
    } else {
      return getBody(context, record);
    }
  }

  private static @NonNull String getGiftSummary(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      return context.getString(R.string.ThreadRecord__you_donated_for_s, messageRecord.getRecipient().getShortDisplayName(context));
    } else if (messageRecord.getViewedReceiptCount() > 0) {
      return context.getString(R.string.ThreadRecord__you_redeemed_a_badge);
    } else {
      return context.getString(R.string.ThreadRecord__s_donated_for_you, messageRecord.getRecipient().getShortDisplayName(context));
    }
  }

  private static @NonNull String getStoryReactionSummary(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      return context.getString(R.string.ThreadRecord__reacted_s_to_their_story, messageRecord.getDisplayBody(context));
    } else {
      return context.getString(R.string.ThreadRecord__reacted_s_to_your_story, messageRecord.getDisplayBody(context));
    }
  }

  private static @NonNull String getPaymentActivationRequestSummary(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      return context.getString(R.string.ThreadRecord_you_sent_request);
    } else {
      return context.getString(R.string.ThreadRecord_wants_you_to_activate_payments, messageRecord.getRecipient().getShortDisplayName(context));
    }
  }

  private static @NonNull String getPaymentActivatedSummary(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      return context.getString(R.string.ThreadRecord_you_activated_payments);
    } else {
      return context.getString(R.string.ThreadRecord_can_accept_payments, messageRecord.getRecipient().getShortDisplayName(context));
    }
  }

  private static @NonNull String getCallLogSummary(@NonNull Context context, @NonNull MessageRecord record) {
    CallTable.Call call = SignalDatabase.calls().getCallByMessageId(record.getId());
    if (call != null) {
      boolean accepted = call.getEvent() == CallTable.Event.ACCEPTED;
      if (call.getDirection() == CallTable.Direction.OUTGOING) {
        if (call.getType() == CallTable.Type.AUDIO_CALL) {
          return context.getString(accepted ? R.string.MessageRecord_outgoing_voice_call : R.string.MessageRecord_unanswered_voice_call);
        } else {
          return context.getString(accepted ? R.string.MessageRecord_outgoing_video_call : R.string.MessageRecord_unanswered_video_call);
        }
      } else {
        boolean isVideoCall = call.getType() == CallTable.Type.VIDEO_CALL;
        boolean isMissed    = call.getEvent() == CallTable.Event.MISSED;

        if (accepted) {
          return context.getString(isVideoCall ? R.string.MessageRecord_incoming_video_call : R.string.MessageRecord_incoming_voice_call);
        } else if (isMissed) {
          return isVideoCall ? context.getString(R.string.MessageRecord_missed_video_call) : context.getString(R.string.MessageRecord_missed_voice_call);
        } else {
          return isVideoCall ? context.getString(R.string.MessageRecord_you_declined_a_video_call) : context.getString(R.string.MessageRecord_you_declined_a_voice_call);
        }
      }
    } else {
      return "";
    }
  }
  
  private static @NonNull String format(@NonNull Context context, @NonNull MessageRecord record, @NonNull String emoji, @StringRes int defaultStringRes) {
    return String.format("%s %s", emoji, getBodyOrDefault(context, record, defaultStringRes));
  }

  private static @NonNull String getBodyOrDefault(@NonNull Context context, @NonNull MessageRecord record, @StringRes int defaultStringRes) {
    return TextUtils.isEmpty(record.getBody()) ? context.getString(defaultStringRes) : getBody(context, record);
  }

  private static @NonNull String getBody(@NonNull Context context, @NonNull MessageRecord record) {
    return MentionUtil.updateBodyWithDisplayNames(context, record, record.getBody()).toString();
  }

  private static @NonNull String getStickerEmoji(@NonNull MessageRecord record) {
    StickerSlide slide = Objects.requireNonNull(((MmsMessageRecord) record).getSlideDeck().getStickerSlide());

    return Util.isEmpty(slide.getEmoji()) ? EmojiStrings.STICKER
                                          : slide.getEmoji();
  }
}
