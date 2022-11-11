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

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase.Status;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.payments.FormatterOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

  private final int           partCount;
  private final boolean       mentionsSelf;
  private final BodyRangeList messageRanges;
  private final Payment       payment;

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
                               int partCount,
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
                               @Nullable Payment payment)
  {
    super(id, body, conversationRecipient, individualRecipient, recipientDeviceId, dateSent,
          dateReceived, dateServer, threadId, Status.STATUS_NONE, deliveryReceiptCount, mailbox, mismatches, failures,
          subscriptionId, expiresIn, expireStarted, viewOnce, slideDeck,
          readReceiptCount, quote, contacts, linkPreviews, unidentified, reactions, remoteDelete, notifiedTimestamp, viewedReceiptCount, receiptTimestamp,
          storyType, parentStoryId, giftBadge);
    this.partCount     = partCount;
    this.mentionsSelf  = mentionsSelf;
    this.messageRanges = messageRanges;
    this.payment       = payment;
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
    if (MmsDatabase.Types.isChatSessionRefresh(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (MmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (isLegacyMessage()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (isPaymentNotification() && payment != null) {
      return new SpannableString(context.getString(R.string.MessageRecord__payment_s, payment.getAmount().toString(FormatterOptions.defaults())));
    }

    return super.getDisplayBody(context);
  }

  public int getPartCount() {
    return partCount;
  }

  public @Nullable BodyRangeList getMessageRanges() {
    return messageRanges;
  }

  @Override
  public boolean hasMessageRanges() {
    return messageRanges != null;
  }

  @Override
  public @NonNull BodyRangeList requireMessageRanges() {
    return Objects.requireNonNull(messageRanges);
  }

  public @Nullable Payment getPayment() {
    return payment;
  }

  public @NonNull MediaMmsMessageRecord withReactions(@NonNull List<ReactionRecord> reactions) {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getPartCount(), getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), reactions, isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment());
  }

  public @NonNull MediaMmsMessageRecord withoutQuote() {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getPartCount(), getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), null, getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment());
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
    SlideDeck                slideDeck        = MmsDatabase.Reader.buildSlideDeck(context, slideAttachments);

    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), slideDeck,
                                     getPartCount(), getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), quote, contacts, linkPreviews, isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), getPayment());
  }

  public @NonNull MediaMmsMessageRecord withPayment(@NonNull Payment payment) {
    return new MediaMmsMessageRecord(getId(), getRecipient(), getIndividualRecipient(), getRecipientDeviceId(), getDateSent(), getDateReceived(), getServerTimestamp(), getDeliveryReceiptCount(), getThreadId(), getBody(), getSlideDeck(),
                                     getPartCount(), getType(), getIdentityKeyMismatches(), getNetworkFailures(), getSubscriptionId(), getExpiresIn(), getExpireStarted(), isViewOnce(),
                                     getReadReceiptCount(), getQuote(), getSharedContacts(), getLinkPreviews(), isUnidentified(), getReactions(), isRemoteDelete(), mentionsSelf,
                                     getNotifiedTimestamp(), getViewedReceiptCount(), getReceiptTimestamp(), getMessageRanges(), getStoryType(), getParentStoryId(), getGiftBadge(), payment);
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
