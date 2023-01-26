/**
 * Copyright (C) 2012 Moxie Marlinspike
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.payments.FormatterOptions;

import java.util.HashMap;
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

public class MediaMmsMessageRecord extends MmsMessageRecord {
  private final static String TAG = Log.tag(MediaMmsMessageRecord.class);

  private final boolean        mentionsSelf;
  private final BodyRangeList  messageRanges;
  private final Payment        payment;
  private final CallTable.Call call;
  private final long           scheduledDate;

  public MediaMmsMessageRecord(long id,
                               Recipient conversationRecipient,
                               Recipient individualRecipient,
                               int recipientDeviceId,
                               long dateSent,
                               long dateReceived,
                               long dateServer,
                               int deliveryReceiptCount,
                               long threadId,
                               String body,
                               @NonNull SlideDeck slideDeck,
                               long mailbox,
                               Set<IdentityKeyMismatch> mismatches,
                               Set<NetworkFailure> failures,
                               int subscriptionId,
                               long expiresIn,
                               long expireStarted,
                               boolean viewOnce,
                               int readReceiptCount,
                               @Nullable Quote quote,
                               @NonNull List<Contact> contacts,
                               @NonNull List<LinkPreview> linkPreviews,
                               boolean unidentified,
                               @NonNull List<ReactionRecord> reactions,
                               boolean remoteDelete,
                               boolean mentionsSelf,
                               long notifiedTimestamp,
                               int viewedReceiptCount,
                               long receiptTimestamp,
                               @Nullable BodyRangeList messageRanges,
                               @NonNull StoryType storyType,
                               @Nullable ParentStoryId parentStoryId,
                               @Nullable GiftBadge giftBadge,
                               @Nullable Payment payment,
                               @Nullable CallTable.Call call,
                               long scheduledDate)
  {
    super(id, body, conversationRecipient, individualRecipient, recipientDeviceId, dateSent,
          dateReceived, dateServer, threadId, Status.STATUS_NONE, deliveryReceiptCount, mailbox, mismatches, failures,
          subscriptionId, expiresIn, expireStarted, viewOnce, slideDeck,
          readReceiptCount, quote, contacts, linkPreviews, unidentified, reactions, remoteDelete, notifiedTimestamp, viewedReceiptCount, receiptTimestamp,
          storyType, parentStoryId, giftBadge);
    this.mentionsSelf  = mentionsSelf;
    this.messageRanges = messageRanges;
    this.payment       = payment;
    this.call          = call;
    this.scheduledDate = scheduledDate;
  }

  @Override
  public boolean hasSelfMention() {
    return mentionsSelf;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
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
    }

    return super.getDisplayBody(context);
  }

  @Override
  public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
    if (isCallLog() && call != null) {
      boolean accepted = call.getEvent() == CallTable.Event.ACCEPTED;
      String callDateString = getCallDateString(context);

      if (call.getDirection() == CallTable.Direction.OUTGOING) {
        if (call.getType() == CallTable.Type.AUDIO_CALL) {
          int updateString = accepted ? R.string.MessageRecord_outgoing_voice_call : R.string.MessageRecord_unanswered_voice_call;
          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(updateString), callDateString), R.drawable.ic_update_audio_call_outgoing_16);
        } else {
          int updateString = accepted ? R.string.MessageRecord_outgoing_video_call : R.string.MessageRecord_unanswered_video_call;
          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(updateString), callDateString), R.drawable.ic_update_video_call_outgoing_16);
        }
      } else {
        boolean isVideoCall = call.getType() == CallTable.Type.VIDEO_CALL;
        boolean isMissed    = call.getEvent() == CallTable.Event.MISSED;

        if (accepted) {
          int updateString = isVideoCall ? R.string.MessageRecord_incoming_video_call : R.string.MessageRecord_incoming_voice_call;
          int icon         = isVideoCall ? R.drawable.ic_update_video_call_incoming_16 : R.drawable.ic_update_audio_call_incoming_16;
          return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(updateString), callDateString), icon);
        } else if (isMissed) {
          return isVideoCall ? staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_missed_video_call), callDateString), R.drawable.ic_update_video_call_missed_16, ContextCompat.getColor(context, R.color.core_red_shade), ContextCompat.getColor(context, R.color.core_red))
                             : staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_missed_voice_call), callDateString), R.drawable.ic_update_audio_call_missed_16, ContextCompat.getColor(context, R.color.core_red_shade), ContextCompat.getColor(context, R.color.core_red));
        } else {
          return isVideoCall ? staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_you_declined_a_video_call), callDateString), R.drawable.ic_update_video_call_incoming_16)
                             : staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_you_declined_a_voice_call), callDateString), R.drawable.ic_update_audio_call_incoming_16);
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

  public @NonNull MediaMmsMessageRecord withReactions(@NonNull List<ReactionRecord> reactions) {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), reactions, isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), getCall(), getScheduledDate());
  }

  public @NonNull MediaMmsMessageRecord withoutQuote() {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), null, getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), getCall(), getScheduledDate());
  }

  public @NonNull MediaMmsMessageRecord withAttachments(@NonNull Context context, @NonNull List<DatabaseAttachment> attachments) {
    Map<AttachmentId, DatabaseAttachment> attachmentIdMap = new HashMap<>();
    for (DatabaseAttachment attachment : attachments) {
      attachmentIdMap.put(attachment.getAttachmentId(), attachment);
    }

    List<Contact>     contacts               = updateContacts(getSharedContacts(), attachmentIdMap);
    Set<Attachment>   contactAttachments     = contacts.stream().map(Contact::getAvatarAttachment).filter(Objects::nonNull).collect(Collectors.toSet());
    List<LinkPreview> linkPreviews           = updateLinkPreviews(getLinkPreviews(), attachmentIdMap);
    Set<Attachment>   linkPreviewAttachments = linkPreviews.stream().map(LinkPreview::getThumbnail).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
    Quote             quote                  = updateQuote(context, getQuote(), attachments);

    List<DatabaseAttachment> slideAttachments = attachments.stream().filter(a -> !contactAttachments.contains(a)).filter(a -> !linkPreviewAttachments.contains(a)).collect(Collectors.toList());
    SlideDeck                slideDeck        = MessageTable.MmsReader.buildSlideDeck(context, slideAttachments);

    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), slideDeck,
                                     getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), quote, contacts, linkPreviews, isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), getCall(), getScheduledDate());
  }

  public @NonNull MediaMmsMessageRecord withPayment(@NonNull Payment payment) {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), payment, getCall(), getScheduledDate());
  }


  public @NonNull MediaMmsMessageRecord withCall(@Nullable CallTable.Call call) {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment(), call, getScheduledDate());
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

  private static @Nullable Quote updateQuote(@NonNull Context context, @Nullable Quote quote, @NonNull List<DatabaseAttachment> attachments) {
    if (quote == null) {
      return null;
    }

    List<DatabaseAttachment> quoteAttachments = attachments.stream().filter(Attachment::isQuote).collect(Collectors.toList());

    return quote.withAttachment(new SlideDeck(context, quoteAttachments));
  }
}
