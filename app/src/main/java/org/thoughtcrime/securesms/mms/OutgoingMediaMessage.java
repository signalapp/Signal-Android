package org.thoughtcrime.securesms.mms;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.ParentStoryId;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class OutgoingMediaMessage {

  private   final Recipient                 recipient;
  protected final String                    body;
  protected final List<Attachment>          attachments;
  private   final long                      sentTimeMillis;
  private   final int                       distributionType;
  private   final int                       subscriptionId;
  private   final long                      expiresIn;
  private   final boolean                   viewOnce;
  private   final QuoteModel                outgoingQuote;
  private   final StoryType                 storyType;
  private   final ParentStoryId             parentStoryId;
  private   final boolean                   isStoryReaction;
  private   final GiftBadge                 giftBadge;

  private   final Set<NetworkFailure>      networkFailures       = new HashSet<>();
  private   final Set<IdentityKeyMismatch> identityKeyMismatches = new HashSet<>();
  private   final List<Contact>            contacts              = new LinkedList<>();
  private   final List<LinkPreview>        linkPreviews          = new LinkedList<>();
  private   final List<Mention>            mentions              = new LinkedList<>();

  public OutgoingMediaMessage(Recipient recipient,
                              String message,
                              List<Attachment> attachments,
                              long sentTimeMillis,
                              int subscriptionId,
                              long expiresIn,
                              boolean viewOnce,
                              int distributionType,
                              @NonNull StoryType storyType,
                              @Nullable ParentStoryId parentStoryId,
                              boolean isStoryReaction,
                              @Nullable QuoteModel outgoingQuote,
                              @NonNull List<Contact> contacts,
                              @NonNull List<LinkPreview> linkPreviews,
                              @NonNull List<Mention> mentions,
                              @NonNull Set<NetworkFailure> networkFailures,
                              @NonNull Set<IdentityKeyMismatch> identityKeyMismatches,
                              @Nullable GiftBadge giftBadge)
  {
    this.recipient             = recipient;
    this.body                  = message;
    this.sentTimeMillis        = sentTimeMillis;
    this.distributionType      = distributionType;
    this.attachments           = attachments;
    this.subscriptionId        = subscriptionId;
    this.expiresIn             = expiresIn;
    this.viewOnce              = viewOnce;
    this.outgoingQuote         = outgoingQuote;
    this.storyType             = storyType;
    this.parentStoryId         = parentStoryId;
    this.isStoryReaction       = isStoryReaction;
    this.giftBadge             = giftBadge;

    this.contacts.addAll(contacts);
    this.linkPreviews.addAll(linkPreviews);
    this.mentions.addAll(mentions);
    this.networkFailures.addAll(networkFailures);
    this.identityKeyMismatches.addAll(identityKeyMismatches);
  }

  public OutgoingMediaMessage(Recipient recipient,
                              SlideDeck slideDeck,
                              String message,
                              long sentTimeMillis,
                              int subscriptionId,
                              long expiresIn,
                              boolean viewOnce,
                              int distributionType,
                              @NonNull StoryType storyType,
                              @Nullable ParentStoryId parentStoryId,
                              boolean isStoryReaction,
                              @Nullable QuoteModel outgoingQuote,
                              @NonNull List<Contact> contacts,
                              @NonNull List<LinkPreview> linkPreviews,
                              @NonNull List<Mention> mentions,
                              @Nullable GiftBadge giftBadge)
  {
    this(recipient,
         buildMessage(slideDeck, message),
         slideDeck.asAttachments(),
         sentTimeMillis,
         subscriptionId,
         expiresIn,
         viewOnce,
         distributionType,
         storyType,
         parentStoryId,
         isStoryReaction,
         outgoingQuote,
         contacts,
         linkPreviews,
         mentions,
         new HashSet<>(),
         new HashSet<>(),
         giftBadge);
  }

  public OutgoingMediaMessage(OutgoingMediaMessage that) {
    this.recipient           = that.getRecipient();
    this.body                = that.body;
    this.distributionType    = that.distributionType;
    this.attachments         = that.attachments;
    this.sentTimeMillis      = that.sentTimeMillis;
    this.subscriptionId      = that.subscriptionId;
    this.expiresIn           = that.expiresIn;
    this.viewOnce            = that.viewOnce;
    this.outgoingQuote       = that.outgoingQuote;
    this.storyType           = that.storyType;
    this.parentStoryId       = that.parentStoryId;
    this.isStoryReaction     = that.isStoryReaction;
    this.giftBadge           = that.giftBadge;

    this.identityKeyMismatches.addAll(that.identityKeyMismatches);
    this.networkFailures.addAll(that.networkFailures);
    this.contacts.addAll(that.contacts);
    this.linkPreviews.addAll(that.linkPreviews);
    this.mentions.addAll(that.mentions);
  }

  public @NonNull OutgoingMediaMessage withExpiry(long expiresIn) {
    return new OutgoingMediaMessage(
        getRecipient(),
        body,
        attachments,
        sentTimeMillis,
        subscriptionId,
        expiresIn,
        viewOnce,
        distributionType,
        storyType,
        parentStoryId,
        isStoryReaction,
        outgoingQuote,
        contacts,
        linkPreviews,
        mentions,
        networkFailures,
        identityKeyMismatches,
        giftBadge
    );
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public String getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public boolean isSecure() {
    return false;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isExpirationUpdate() {
    return false;
  }

  public boolean isPaymentsNotification() {
    return false;
  }

  public boolean isRequestToActivatePayments() {
    return false;
  }

  public boolean isPaymentsActivated() {
    return false;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public @NonNull StoryType getStoryType() {
    return storyType;
  }

  public @Nullable ParentStoryId getParentStoryId() {
    return parentStoryId;
  }

  public boolean isStoryReaction() {
    return isStoryReaction;
  }

  public @Nullable QuoteModel getOutgoingQuote() {
    return outgoingQuote;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
  }

  public @NonNull List<LinkPreview> getLinkPreviews() {
    return linkPreviews;
  }

  public @NonNull List<Mention> getMentions() {
    return mentions;
  }

  public @NonNull Set<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public @NonNull Set<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return identityKeyMismatches;
  }

  public @Nullable GiftBadge getGiftBadge() {
    return giftBadge;
  }

  public boolean isUrgent() {
    return true;
  }

  private static String buildMessage(SlideDeck slideDeck, String message) {
    if (!TextUtils.isEmpty(message) && !TextUtils.isEmpty(slideDeck.getBody())) {
      return slideDeck.getBody() + "\n\n" + message;
    } else if (!TextUtils.isEmpty(message)) {
      return message;
    } else {
      return slideDeck.getBody();
    }
  }
}
