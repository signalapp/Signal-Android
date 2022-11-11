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
      return context.getString(R.string.ThreadRecord__you_sent_a_gift);
    } else if (messageRecord.getViewedReceiptCount() > 0) {
      return context.getString(R.string.ThreadRecord__you_redeemed_a_gift_badge);
    } else {
      return context.getString(R.string.ThreadRecord__you_received_a_gift);
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
