package org.thoughtcrime.securesms.database;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ThreadBodyUtil {

  private static final String TAG = Log.tag(ThreadBodyUtil.class);

  private ThreadBodyUtil() {
  }

  public static @NonNull ThreadBody getFormattedBodyFor(@NonNull Context context, @NonNull MessageRecord record) {
    if (record.isMms()) {
      return getFormattedBodyForMms(context, (MmsMessageRecord) record, null);
    }

    return new ThreadBody(record.getBody());
  }

  public static @NonNull CharSequence getFormattedBodyForNotification(@NonNull Context context, @NonNull MessageRecord record, @Nullable CharSequence bodyOverride) {
    return getFormattedBodyForMms(context, (MmsMessageRecord) record, bodyOverride).body;
  }

  private static @NonNull ThreadBody getFormattedBodyForMms(@NonNull Context context, @NonNull MmsMessageRecord record, @Nullable CharSequence bodyOverride) {
    if (record.getSharedContacts().size() > 0) {
      Contact contact = record.getSharedContacts().get(0);

      return new ThreadBody(ContactUtil.getStringSummary(context, contact).toString());
    } else if (record.getSlideDeck().getDocumentSlide() != null) {
      return format(context, record, EmojiStrings.FILE, R.string.ThreadRecord_file, bodyOverride);
    } else if (record.getSlideDeck().getAudioSlide() != null) {
      return format(context, record, EmojiStrings.AUDIO, R.string.ThreadRecord_voice_message, bodyOverride);
    } else if (MessageRecordUtil.hasSticker(record)) {
      String emoji = getStickerEmoji(record);
      return format(context, record, emoji, R.string.ThreadRecord_sticker, bodyOverride);
    } else if (MessageRecordUtil.hasGiftBadge(record)) {
      return format(EmojiStrings.GIFT, getGiftSummary(context, record), null);
    } else if (MessageRecordUtil.isStoryReaction(record)) {
      return new ThreadBody(getStoryReactionSummary(context, record));
    } else if (record.isPaymentNotification() || record.isPaymentTombstone()) {
      return format(EmojiStrings.CARD, context.getString(R.string.ThreadRecord_payment), null);
    } else if (record.isPaymentsRequestToActivate()) {
      return format(EmojiStrings.CARD, getPaymentActivationRequestSummary(context, record), null);
    } else if (record.isPaymentsActivated()) {
      return format(EmojiStrings.CARD, getPaymentActivatedSummary(context, record), null);
    } else if (record.isCallLog() && !record.isGroupCall()) {
      return new ThreadBody(getCallLogSummary(context, record));
    } else if (MessageRecordUtil.isScheduled(record)) {
      return new ThreadBody(context.getString(R.string.ThreadRecord_scheduled_message));
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
      return format(context, record, EmojiStrings.GIF, R.string.ThreadRecord_gif, bodyOverride);
    } else if (hasVideo) {
      return format(context, record, EmojiStrings.VIDEO, R.string.ThreadRecord_video, bodyOverride);
    } else if (hasImage) {
      return format(context, record, EmojiStrings.PHOTO, R.string.ThreadRecord_photo, bodyOverride);
    } else if (TextUtils.isEmpty(record.getBody())) {
      return new ThreadBody(context.getString(R.string.ThreadRecord_media_message));
    } else {
      return getBody(context, record);
    }
  }

  private static @NonNull String getGiftSummary(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      return context.getString(R.string.ThreadRecord__you_donated_for_s, messageRecord.getToRecipient().getShortDisplayName(context));
    } else if (messageRecord.isViewed()) {
      return context.getString(R.string.ThreadRecord__you_redeemed_a_badge);
    } else {
      return context.getString(R.string.ThreadRecord__s_donated_for_you, messageRecord.getFromRecipient().getShortDisplayName(context));
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
      return context.getString(R.string.ThreadRecord_wants_you_to_activate_payments, messageRecord.getFromRecipient().getShortDisplayName(context));
    }
  }

  private static @NonNull String getPaymentActivatedSummary(@NonNull Context context, @NonNull MessageRecord messageRecord) {
    if (messageRecord.isOutgoing()) {
      return context.getString(R.string.ThreadRecord_you_activated_payments);
    } else {
      return context.getString(R.string.ThreadRecord_can_accept_payments, messageRecord.getFromRecipient().getShortDisplayName(context));
    }
  }

  private static @NonNull String getCallLogSummary(@NonNull Context context, @NonNull MessageRecord record) {
    CallTable.Call call = SignalDatabase.calls().getCallByMessageId(record.getId());
    if (call != null) {
      boolean accepted = call.getEvent() == CallTable.Event.ACCEPTED;
      if (call.getDirection() == CallTable.Direction.OUTGOING) {
        if (call.getType() == CallTable.Type.AUDIO_CALL) {
          return context.getString(R.string.MessageRecord_outgoing_voice_call);
        } else {
          return context.getString(R.string.MessageRecord_outgoing_video_call);
        }
      } else {
        boolean isVideoCall = call.getType() == CallTable.Type.VIDEO_CALL;

        if (accepted || !call.isDisplayedAsMissedCallInUi()) {
          return context.getString(isVideoCall ? R.string.MessageRecord_incoming_video_call : R.string.MessageRecord_incoming_voice_call);
        } else {
          if (call.getEvent() == CallTable.Event.MISSED_NOTIFICATION_PROFILE) {
            return isVideoCall ? context.getString(R.string.MessageRecord_missed_video_call_notification_profile) : context.getString(R.string.MessageRecord_missed_voice_call_notification_profile);
          } else {
            return isVideoCall ? context.getString(R.string.MessageRecord_missed_video_call) : context.getString(R.string.MessageRecord_missed_voice_call);
          }
        }
      }
    } else {
      return "";
    }
  }

  private static @NonNull ThreadBody format(@NonNull Context context,
                                            @NonNull MessageRecord record,
                                            @NonNull String emoji,
                                            @StringRes int defaultStringRes,
                                            @Nullable CharSequence bodyOverride)
  {
    CharSequence body;
    List<BodyAdjustment> adjustments = null;

    if (!TextUtils.isEmpty(bodyOverride)) {
      body = bodyOverride;
    } else if (TextUtils.isEmpty(record.getBody())) {
      body = context.getString(defaultStringRes);
    } else {
      ThreadBody threadBody = getBody(context, record);
      body = threadBody.getBody();
      adjustments = threadBody.getBodyAdjustments();
    }

    return format(emoji, body, adjustments);
  }

  private static @NonNull ThreadBody format(@NonNull CharSequence prefix, @NonNull CharSequence body, @Nullable List<BodyAdjustment> adjustments) {
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(prefix)
           .append(" ")
           .append(body);
    return new ThreadBody(builder, prefix.length() + 1, adjustments != null ? adjustments : Collections.emptyList());
  }

  private static @NonNull ThreadBody getBody(@NonNull Context context, @NonNull MessageRecord record) {
    MentionUtil.UpdatedBodyAndMentions updated = MentionUtil.updateBodyWithDisplayNames(context, record, record.getBody());
    //noinspection ConstantConditions
    return new ThreadBody(updated.getBody(), updated.getBodyAdjustments());
  }

  private static @NonNull String getStickerEmoji(@NonNull MessageRecord record) {
    StickerSlide slide = Objects.requireNonNull(((MmsMessageRecord) record).getSlideDeck().getStickerSlide());

    return Util.isEmpty(slide.getEmoji()) ? EmojiStrings.STICKER
                                          : slide.getEmoji();
  }

  public static class ThreadBody {
    private final CharSequence         body;
    private final List<BodyAdjustment> bodyAdjustments;

    public ThreadBody(@NonNull CharSequence body) {
      this(body, 0, Collections.emptyList());
    }

    public ThreadBody(@NonNull CharSequence body, @NonNull List<BodyAdjustment> bodyAdjustments) {
      this(body, 0, bodyAdjustments);
    }

    public ThreadBody(@NonNull CharSequence body, int startOffset, @NonNull List<BodyAdjustment> bodyAdjustments) {
      this.body = body;
      if (startOffset == 0) {
        this.bodyAdjustments = bodyAdjustments;
      } else {
        ArrayList<BodyAdjustment> updatedAdjustments = new ArrayList<>(bodyAdjustments.size() + 1);
        updatedAdjustments.add(new BodyAdjustment(0, 0, startOffset));
        updatedAdjustments.addAll(bodyAdjustments);

        this.bodyAdjustments = updatedAdjustments;
      }
    }

    public @NonNull CharSequence getBody() {
      return body;
    }

    public @NonNull List<BodyAdjustment> getBodyAdjustments() {
      return bodyAdjustments;
    }
  }
}
