/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MessageTable.Status;
import org.thoughtcrime.securesms.database.MessageTypes;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.CryptoValue;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.payments.CryptoValueUtil;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.payments.FormatterOptions;
import org.whispersystems.signalservice.api.payments.Money;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MmsMessageRecord extends MessageRecord {
  private final static String TAG = Log.tag(MmsMessageRecord.class);

  private final SlideDeck         slideDeck;
  private final Quote             quote;
  private final List<Contact>     contacts     = new LinkedList<>();
  private final List<LinkPreview> linkPreviews = new LinkedList<>();
  private final StoryType         storyType;
  private final ParentStoryId     parentStoryId;
  private final GiftBadge         giftBadge;
  private final boolean           viewOnce;
  
  private final boolean        mentionsSelf;
  private final BodyRangeList  messageRanges;
  private final Payment        payment;
  private final CallTable.Call call;
  private final long           scheduledDate;
  private final MessageId      latestRevisionId;
  private final boolean        isRead;

  public MmsMessageRecord(long id,
                          Recipient fromRecipient,
                          int fromDeviceId,
                          Recipient toRecipient,
                          long dateSent,
                          long dateReceived,
                          long dateServer,
                          boolean hasDeliveryReceipt,
                          long threadId,
                          String body,
                          @NonNull SlideDeck slideDeck,
                          long mailbox,
                          Set<IdentityKeyMismatch> mismatches,
                          Set<NetworkFailure> failures,
                          int subscriptionId,
                          long expiresIn,
                          long expireStarted,
                          int expireTimerVersion,
                          boolean viewOnce,
                          boolean hasReadReceipt,
                          @Nullable Quote quote,
                          @NonNull List<Contact> contacts,
                          @NonNull List<LinkPreview> linkPreviews,
                          boolean unidentified,
                          @NonNull List<ReactionRecord> reactions,
                          boolean remoteDelete,
                          boolean mentionsSelf,
                          long notifiedTimestamp,
                          boolean viewed,
                          long receiptTimestamp,
                          @Nullable BodyRangeList messageRanges,
                          @NonNull StoryType storyType,
                          @Nullable ParentStoryId parentStoryId,
                          @Nullable GiftBadge giftBadge,
                          @Nullable Payment payment,
                          @Nullable CallTable.Call call,
                          long scheduledDate,
                          @Nullable MessageId latestRevisionId,
                          @Nullable MessageId originalMessageId,
                          int revisionNumber,
                          boolean isRead,
                          @Nullable MessageExtras messageExtras)
  {
    super(id, body, fromRecipient, fromDeviceId, toRecipient,
          dateSent, dateReceived, dateServer, threadId, Status.STATUS_NONE, hasDeliveryReceipt,
          mailbox, mismatches, failures, subscriptionId, expiresIn, expireStarted, expireTimerVersion, hasReadReceipt,
          unidentified, reactions, remoteDelete, notifiedTimestamp, viewed, receiptTimestamp, originalMessageId, revisionNumber, messageExtras);

    this.slideDeck        = slideDeck;
    this.quote            = quote;
    this.viewOnce         = viewOnce;
    this.storyType        = storyType;
    this.parentStoryId    = parentStoryId;
    this.giftBadge        = giftBadge;
    this.mentionsSelf     = mentionsSelf;
    this.messageRanges    = messageRanges;
    this.payment          = payment;
    this.call             = call;
    this.scheduledDate    = scheduledDate;
    this.latestRevisionId = latestRevisionId;
    this.isRead           = isRead;

    this.contacts.addAll(contacts);
    this.linkPreviews.addAll(linkPreviews);
  }
  
  @Override
  public boolean isMms() {
    return true;
  }

  @NonNull
  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  @Override
  public boolean isMediaPending() {
    for (Slide slide : getSlideDeck().getSlides()) {
      if (slide.isInProgress() || slide.isPendingDownload()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean isViewOnce() {
    return viewOnce;
  }

  public @NonNull StoryType getStoryType() {
    return storyType;
  }

  public @Nullable ParentStoryId getParentStoryId() {
    return parentStoryId;
  }

  public boolean containsMediaSlide() {
    return slideDeck.containsMediaSlide();
  }

  public @Nullable Quote getQuote() {
    return quote;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
  }

  public @NonNull List<LinkPreview> getLinkPreviews() {
    return linkPreviews;
  }

  public @Nullable GiftBadge getGiftBadge() {
    return giftBadge;
  }

  @Override
  public boolean hasSelfMention() {
    return mentionsSelf;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  public boolean isRead() {
    return isRead;
  }

  @Override
  @WorkerThread
  public SpannableString getDisplayBody(@NonNull Context context) {
    if (MessageTypes.isChatSessionRefresh(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MessageTypes.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (MessageTypes.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (isLegacyMessage()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (isPaymentNotification() && payment != null) {
      return new SpannableString(context.getString(R.string.MessageRecord__payment_s, payment.getAmount().toString(FormatterOptions.defaults())));
    } else if (isPaymentTombstone() || isPaymentNotification()) {
      MessageExtras extras = getMessageExtras();

      Money amount = null;
      if (extras != null && extras.paymentTombstone != null && extras.paymentTombstone.amount != null) {
        amount = CryptoValueUtil.cryptoValueToMoney(extras.paymentTombstone.amount);
      }
      if (amount == null) {
        return new SpannableString(context.getString(R.string.MessageRecord__payment_tombstone));
      } else {
        return new SpannableString(context.getString(R.string.MessageRecord__payment_s, amount.toString(FormatterOptions.defaults())));
      }
    }

    return super.getDisplayBody(context);
  }

  @Override
  public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
    if (isCallLog() && call != null && !(call.getType() == CallTable.Type.GROUP_CALL)) {
      boolean accepted       = call.getEvent() == CallTable.Event.ACCEPTED;
      String  callDateString = getCallDateString(context);

      if (call.getDirection() == CallTable.Direction.OUTGOING) {
        if (call.getType() == CallTable.Type.AUDIO_CALL) {
          int updateString = R.string.MessageRecord_outgoing_voice_call;
          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(updateString), callDateString), R.drawable.ic_update_audio_call_outgoing_16);
        } else {
          int updateString = R.string.MessageRecord_outgoing_video_call;
          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(updateString), callDateString), R.drawable.ic_update_video_call_outgoing_16);
        }
      } else {
        boolean isVideoCall = call.getType() == CallTable.Type.VIDEO_CALL;

        if (accepted || !call.isDisplayedAsMissedCallInUi()) {
          int updateString = isVideoCall ? R.string.MessageRecord_incoming_video_call : R.string.MessageRecord_incoming_voice_call;
          int icon         = isVideoCall ? R.drawable.ic_update_video_call_incoming_16 : R.drawable.ic_update_audio_call_incoming_16;
          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(updateString), callDateString), icon);
        } else {
          int icon = isVideoCall ? R.drawable.ic_update_video_call_missed_16 : R.drawable.ic_update_audio_call_missed_16;
          int message;
          if (call.getEvent() == CallTable.Event.MISSED_NOTIFICATION_PROFILE) {
            message = isVideoCall ? R.string.MessageRecord_missed_video_call_notification_profile : R.string.MessageRecord_missed_voice_call_notification_profile;
          } else {
            message = isVideoCall ? R.string.MessageRecord_missed_video_call : R.string.MessageRecord_missed_voice_call;
          }

          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date,
                                                           context.getString(message),
                                                           callDateString),
                                         icon,
                                         ContextCompat.getColor(context, R.color.core_red_shade),
                                         ContextCompat.getColor(context, R.color.core_red));
        }
      }
    }
    return super.getUpdateDisplayBody(context, recipientClickHandler);
  }

  @Override
  public @Nullable BodyRangeList getMessageRanges() {
    return messageRanges;
  }

  @Override
  public @NonNull BodyRangeList requireMessageRanges() {
    return Objects.requireNonNull(messageRanges);
  }

  public @Nullable Payment getPayment() {
    return payment;
  }

  public @Nullable CallTable.Call getCall() {
    return call;
  }

  public long getScheduledDate() {
    return scheduledDate;
  }

  public @Nullable MessageId getLatestRevisionId() {
    return latestRevisionId;
  }

  @Override
  public boolean canDeleteSync() {
    return (isSent() || MessageTypes.isInboxType(type)) &&
           (isSecure() || isPush()) &&
           (type & MessageTypes.GROUP_MASK) == 0 &&
           (type & MessageTypes.KEY_EXCHANGE_MASK) == 0 &&
           !isReportedSpam() &&
           !isMessageRequestAccepted() &&
           storyType == StoryType.NONE &&
           getDateSent() > 0 &&
           (parentStoryId == null || parentStoryId.isDirectReply());
  }

  public @NonNull MmsMessageRecord withReactions(@NonNull List<ReactionRecord> reactions) {
    return new MmsMessageRecord(getId(), getFromRecipient(), getFromDeviceId(), getToRecipient(), getDateSent(), getDateReceived(), getServerTimestamp(), hasDeliveryReceipt(), getThreadId(), getBody(), getSlideDeck(),
                                getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), getExpireTimerVersion(), isViewOnce(),
                                hasReadReceipt(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), reactions, isRemoteDelete(), mentionsSelf,
                                getNotifiedTimestamp(), isViewed(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), getCall(), getScheduledDate(), getLatestRevisionId(),
                                getOriginalMessageId(), getRevisionNumber(), isRead(), getMessageExtras());
  }

  public @NonNull MmsMessageRecord withoutQuote() {
    return new MmsMessageRecord(getId(), getFromRecipient(), getFromDeviceId(), getToRecipient(), getDateSent(), getDateReceived(), getServerTimestamp(), hasDeliveryReceipt(), getThreadId(), getBody(), getSlideDeck(),
                                getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), getExpireTimerVersion(), isViewOnce(),
                                hasReadReceipt(), null, getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                getNotifiedTimestamp(), isViewed(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), getCall(), getScheduledDate(), getLatestRevisionId(),
                                getOriginalMessageId(), getRevisionNumber(), isRead(), getMessageExtras());
  }

  public @NonNull MmsMessageRecord withAttachments(@NonNull List<DatabaseAttachment> attachments) {
    Map<AttachmentId, DatabaseAttachment> attachmentIdMap = new HashMap<>();
    for (DatabaseAttachment attachment : attachments) {
      attachmentIdMap.put(attachment.attachmentId, attachment);
    }

    List<Contact>     contacts               = updateContacts(getSharedContacts(), attachmentIdMap);
    Set<Attachment>   contactAttachments     = contacts.stream().map(Contact::getAvatarAttachment).filter(Objects::nonNull).collect(Collectors.toSet());
    List<LinkPreview> linkPreviews           = updateLinkPreviews(getLinkPreviews(), attachmentIdMap);
    Set<Attachment>   linkPreviewAttachments = linkPreviews.stream().map(LinkPreview::getThumbnail).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
    Quote             quote                  = updateQuote(getQuote(), attachments);

    List<DatabaseAttachment> slideAttachments = attachments.stream().filter(a -> !contactAttachments.contains(a)).filter(a -> !linkPreviewAttachments.contains(a)).collect(Collectors.toList());
    SlideDeck                slideDeck        = MessageTable.MmsReader.buildSlideDeck(slideAttachments);

    return new MmsMessageRecord(getId(), getFromRecipient(), getFromDeviceId(), getToRecipient(), getDateSent(), getDateReceived(), getServerTimestamp(), hasDeliveryReceipt(), getThreadId(), getBody(), slideDeck,
                                getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), getExpireTimerVersion(), isViewOnce(),
                                hasReadReceipt(), quote, contacts, linkPreviews, isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                getNotifiedTimestamp(), isViewed(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), getCall(), getScheduledDate(), getLatestRevisionId(),
                                getOriginalMessageId(), getRevisionNumber(), isRead(), getMessageExtras());
  }

  public @NonNull MmsMessageRecord withPayment(@NonNull Payment payment) {
    return new MmsMessageRecord(getId(), getFromRecipient(), getFromDeviceId(), getToRecipient(), getDateSent(), getDateReceived(), getServerTimestamp(), hasDeliveryReceipt(), getThreadId(), getBody(), getSlideDeck(),
                                getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), getExpireTimerVersion(), isViewOnce(),
                                hasReadReceipt(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                getNotifiedTimestamp(), isViewed(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), payment, getCall(), getScheduledDate(), getLatestRevisionId(),
                                getOriginalMessageId(), getRevisionNumber(), isRead(), getMessageExtras());
  }


  public @NonNull MmsMessageRecord withCall(@Nullable CallTable.Call call) {
    return new MmsMessageRecord(getId(), getFromRecipient(), getFromDeviceId(), getToRecipient(), getDateSent(), getDateReceived(), getServerTimestamp(), hasDeliveryReceipt(), getThreadId(), getBody(), getSlideDeck(),
                                getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), getExpireTimerVersion(), isViewOnce(),
                                hasReadReceipt(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                getNotifiedTimestamp(), isViewed(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), call, getScheduledDate(), getLatestRevisionId(),
                                getOriginalMessageId(), getRevisionNumber(), isRead(), getMessageExtras());
  }

  private static @NonNull List<Contact> updateContacts(@NonNull List<Contact> contacts, @NonNull Map<AttachmentId, DatabaseAttachment> attachmentIdMap) {
    return contacts.stream()
                   .map(contact -> {
                     if (contact.getAvatar() != null) {
                       DatabaseAttachment attachment    = attachmentIdMap.get(contact.getAvatar().getAttachmentId());
                       Contact.Avatar     updatedAvatar = new Contact.Avatar(contact.getAvatar().getAttachmentId(),
                                                                             attachment,
                                                                             contact.getAvatar().isProfile());

                       return new Contact(contact, updatedAvatar);
                     } else {
                       return contact;
                     }
                   })
                   .collect(Collectors.toList());
  }

  private static @NonNull List<LinkPreview> updateLinkPreviews(@NonNull List<LinkPreview> linkPreviews, @NonNull Map<AttachmentId, DatabaseAttachment> attachmentIdMap) {
    return linkPreviews.stream()
                       .map(preview -> {
                         if (preview.getAttachmentId() != null) {
                           DatabaseAttachment attachment = attachmentIdMap.get(preview.getAttachmentId());
                           if (attachment != null) {
                             return new LinkPreview(preview.getUrl(), preview.getTitle(), preview.getDescription(), preview.getDate(), attachment);
                           } else {
                             return preview;
                           }
                         } else {
                           return preview;
                         }
                       })
                       .collect(Collectors.toList());
  }

  private static @Nullable Quote updateQuote(@Nullable Quote quote, @NonNull List<DatabaseAttachment> attachments) {
    if (quote == null) {
      return null;
    }

    List<DatabaseAttachment> quoteAttachments = attachments.stream().filter(a -> a.quote).collect(Collectors.toList());

    return quote.withAttachment(new SlideDeck(quoteAttachments));
  }
}
