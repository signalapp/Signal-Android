package org.session.libsession.messaging.messages.signal;

import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.attachments.PointerAttachment;
import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage;
import org.session.libsession.utilities.Contact;
import org.session.libsession.utilities.Address;
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview;
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel;
import org.session.libsession.utilities.GroupUtil;
import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.messages.SignalServiceAttachment;
import org.session.libsignal.messages.SignalServiceGroup;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class IncomingMediaMessage {

  private final Address       from;
  private final Address       groupId;
  private final String        body;
  private final boolean       push;
  private final long          sentTimeMillis;
  private final int           subscriptionId;
  private final long          expiresIn;
  private final boolean       expirationUpdate;
  private final boolean       unidentified;

  private final DataExtractionNotificationInfoMessage dataExtractionNotification;
  private final QuoteModel                            quote;

  private final List<Attachment>  attachments    = new LinkedList<>();
  private final List<Contact>     sharedContacts = new LinkedList<>();
  private final List<LinkPreview> linkPreviews   = new LinkedList<>();

  public IncomingMediaMessage(Address from,
                              long sentTimeMillis,
                              int subscriptionId,
                              long expiresIn,
                              boolean expirationUpdate,
                              boolean unidentified,
                              Optional<String> body,
                              Optional<SignalServiceGroup> group,
                              Optional<List<SignalServiceAttachment>> attachments,
                              Optional<QuoteModel> quote,
                              Optional<List<Contact>> sharedContacts,
                              Optional<List<LinkPreview>> linkPreviews,
                              Optional<DataExtractionNotificationInfoMessage> dataExtractionNotification)
  {
    this.push                       = true;
    this.from                       = from;
    this.sentTimeMillis             = sentTimeMillis;
    this.body                       = body.orNull();
    this.subscriptionId             = subscriptionId;
    this.expiresIn                  = expiresIn;
    this.expirationUpdate           = expirationUpdate;
    this.dataExtractionNotification = dataExtractionNotification.orNull();
    this.quote                      = quote.orNull();
    this.unidentified               = unidentified;

    if (group.isPresent()) this.groupId = Address.fromSerialized(GroupUtil.INSTANCE.getEncodedId(group.get()));
    else                   this.groupId = null;

    this.attachments.addAll(PointerAttachment.forPointers(attachments));
    this.sharedContacts.addAll(sharedContacts.or(Collections.emptyList()));
    this.linkPreviews.addAll(linkPreviews.or(Collections.emptyList()));
  }

  public static IncomingMediaMessage from(VisibleMessage message,
                                          Address from,
                                          long expiresIn,
                                          Optional<SignalServiceGroup> group,
                                          List<SignalServiceAttachment> attachments,
                                          Optional<QuoteModel> quote,
                                          Optional<List<LinkPreview>> linkPreviews)
  {
    return new IncomingMediaMessage(from, message.getSentTimestamp(), -1, expiresIn, false,
            false, Optional.fromNullable(message.getText()), group, Optional.fromNullable(attachments), quote, Optional.absent(), linkPreviews, Optional.absent());
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public String getBody() {
    return body;
  }

  public List<Attachment> getAttachments() {
    return attachments;
  }

  public Address getFrom() {
    return from;
  }

  public Address getGroupId() {
    return groupId;
  }

  public boolean isPushMessage() {
    return push;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public long getSentTimeMillis() {
    return sentTimeMillis;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public boolean isGroupMessage() {
    return groupId != null;
  }

  public boolean isScreenshotDataExtraction() {
    if (dataExtractionNotification == null) return false;
    else {
      return dataExtractionNotification.getKind() == DataExtractionNotificationInfoMessage.Kind.SCREENSHOT;
    }
  }

  public boolean isMediaSavedDataExtraction() {
    if (dataExtractionNotification == null) return false;
    else {
      return dataExtractionNotification.getKind() == DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED;
    }
  }

  public QuoteModel getQuote() {
    return quote;
  }

  public List<Contact> getSharedContacts() {
    return sharedContacts;
  }

  public List<LinkPreview> getLinkPreviews() {
    return linkPreviews;
  }

  public boolean isUnidentified() {
    return unidentified;
  }
}
