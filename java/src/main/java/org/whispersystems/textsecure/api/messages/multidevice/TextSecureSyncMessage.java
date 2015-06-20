package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;

public class TextSecureSyncMessage {

  private final Optional<SentTranscriptMessage> sent;
  private final Optional<TextSecureAttachment>  contacts;
  private final Optional<TextSecureGroup>       group;
  private final Optional<RequestMessage>        request;

  public TextSecureSyncMessage() {
    this.sent     = Optional.absent();
    this.contacts = Optional.absent();
    this.group    = Optional.absent();
    this.request  = Optional.absent();
  }

  public TextSecureSyncMessage(SentTranscriptMessage sent) {
    this.sent     = Optional.of(sent);
    this.contacts = Optional.absent();
    this.group    = Optional.absent();
    this.request  = Optional.absent();
  }

  public TextSecureSyncMessage(TextSecureAttachment contacts) {
    this.contacts = Optional.of(contacts);
    this.sent     = Optional.absent();
    this.group    = Optional.absent();
    this.request  = Optional.absent();
  }

  public TextSecureSyncMessage(TextSecureGroup group) {
    this.group    = Optional.of(group);
    this.sent     = Optional.absent();
    this.contacts = Optional.absent();
    this.request  = Optional.absent();
  }

  public TextSecureSyncMessage(RequestMessage contactsRequest) {
    this.request  = Optional.of(contactsRequest);
    this.sent     = Optional.absent();
    this.contacts = Optional.absent();
    this.group    = Optional.absent();
  }

  public Optional<SentTranscriptMessage> getSent() {
    return sent;
  }

  public Optional<TextSecureGroup> getGroup() {
    return group;
  }

  public Optional<TextSecureAttachment> getContacts() {
    return contacts;
  }

  public Optional<RequestMessage> getRequest() {
    return request;
  }

}
