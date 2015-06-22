package org.whispersystems.textsecure.api.messages.multidevice;

import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.TextSecureAttachment;
import org.whispersystems.textsecure.api.messages.TextSecureGroup;

public class TextSecureSyncMessage {

  private final Optional<SentTranscriptMessage> sent;
  private final Optional<TextSecureAttachment>  contacts;
  private final Optional<TextSecureAttachment>  groups;
  private final Optional<RequestMessage>        request;

  private TextSecureSyncMessage(Optional<SentTranscriptMessage> sent,
                                Optional<TextSecureAttachment>  contacts,
                                Optional<TextSecureAttachment>  groups,
                                Optional<RequestMessage>        request)
  {
    this.sent     = sent;
    this.contacts = contacts;
    this.groups   = groups;
    this.request  = request;
  }

  public static TextSecureSyncMessage forSentTranscript(SentTranscriptMessage sent) {
    return new TextSecureSyncMessage(Optional.of(sent),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.<RequestMessage>absent());
  }

  public static TextSecureSyncMessage forContacts(TextSecureAttachment contacts) {
    return new TextSecureSyncMessage(Optional.<SentTranscriptMessage>absent(),
                                     Optional.of(contacts),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.<RequestMessage>absent());
  }

  public static TextSecureSyncMessage forGroups(TextSecureAttachment groups) {
    return new TextSecureSyncMessage(Optional.<SentTranscriptMessage>absent(),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.of(groups),
                                     Optional.<RequestMessage>absent());
  }

  public static TextSecureSyncMessage forRequest(RequestMessage request) {
    return new TextSecureSyncMessage(Optional.<SentTranscriptMessage>absent(),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.of(request));
  }

  public static TextSecureSyncMessage empty() {
    return new TextSecureSyncMessage(Optional.<SentTranscriptMessage>absent(),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.<TextSecureAttachment>absent(),
                                     Optional.<RequestMessage>absent());
  }

  public Optional<SentTranscriptMessage> getSent() {
    return sent;
  }

  public Optional<TextSecureAttachment> getGroups() {
    return groups;
  }

  public Optional<TextSecureAttachment> getContacts() {
    return contacts;
  }

  public Optional<RequestMessage> getRequest() {
    return request;
  }

}
