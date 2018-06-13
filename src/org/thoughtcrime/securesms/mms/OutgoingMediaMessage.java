package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.LinkedList;
import java.util.List;

public class OutgoingMediaMessage {

  private   final Recipient        recipient;
  protected final String           body;
  protected final List<Attachment> attachments;
  private   final long             sentTimeMillis;
  private   final int              distributionType;
  private   final int              subscriptionId;
  private   final long             expiresIn;
  private   final QuoteModel       outgoingQuote;
  private   final List<Contact>    contacts = new LinkedList<>();

  public OutgoingMediaMessage(Recipient recipient, String message,
                              List<Attachment> attachments, long sentTimeMillis,
                              int subscriptionId, long expiresIn,
                              int distributionType, @Nullable QuoteModel outgoingQuote,
                              @NonNull List<Contact> contacts)
  {
    this.recipient        = recipient;
    this.body             = message;
    this.sentTimeMillis   = sentTimeMillis;
    this.distributionType = distributionType;
    this.attachments      = attachments;
    this.subscriptionId   = subscriptionId;
    this.expiresIn        = expiresIn;
    this.outgoingQuote    = outgoingQuote;

    this.contacts.addAll(contacts);
  }

  public OutgoingMediaMessage(Recipient recipient, SlideDeck slideDeck, String message, long sentTimeMillis, int subscriptionId, long expiresIn, int distributionType, @Nullable QuoteModel outgoingQuote, @NonNull List<Contact> contacts)
  {
    this(recipient,
         buildMessage(slideDeck, message),
         slideDeck.asAttachments(),
         sentTimeMillis, subscriptionId,
         expiresIn, distributionType, outgoingQuote, contacts);
  }

  public OutgoingMediaMessage(OutgoingMediaMessage that) {
    this.recipient           = that.getRecipient();
    this.body                = that.body;
    this.distributionType    = that.distributionType;
    this.attachments         = that.attachments;
    this.sentTimeMillis      = that.sentTimeMillis;
    this.subscriptionId      = that.subscriptionId;
    this.expiresIn           = that.expiresIn;
    this.outgoingQuote       = that.outgoingQuote;

    this.contacts.addAll(that.contacts);
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

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public @Nullable QuoteModel getOutgoingQuote() {
    return outgoingQuote;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return contacts;
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
