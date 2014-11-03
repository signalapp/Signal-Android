package org.whispersystems.textsecure.api.messages;

import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.List;

public class TextSecureMessage {

  private final long                                 timestamp;
  private final Optional<List<TextSecureAttachment>> attachments;
  private final Optional<String>                     body;
  private final Optional<TextSecureGroup>            group;
  private final boolean                              secure;
  private final boolean                              endSession;

  public TextSecureMessage(long timestamp, String body) {
    this(timestamp, null, body);
  }

  public TextSecureMessage(long timestamp, List<TextSecureAttachment> attachments, String body) {
    this(timestamp, null, attachments, body);
  }

  public TextSecureMessage(long timestamp, TextSecureGroup group, List<TextSecureAttachment> attachments, String body) {
    this(timestamp, group, attachments, body, true, false);
  }

  public TextSecureMessage(long timestamp, TextSecureGroup group, List<TextSecureAttachment> attachments, String body, boolean secure, boolean endSession) {
    this.timestamp   = timestamp;
    this.attachments = Optional.fromNullable(attachments);
    this.body        = Optional.fromNullable(body);
    this.group       = Optional.fromNullable(group);
    this.secure      = secure;
    this.endSession  = endSession;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Optional<List<TextSecureAttachment>> getAttachments() {
    return attachments;
  }

  public Optional<String> getBody() {
    return body;
  }

  public Optional<TextSecureGroup> getGroupInfo() {
    return group;
  }

  public boolean isSecure() {
    return secure;
  }

  public boolean isEndSession() {
    return endSession;
  }

  public boolean isGroupUpdate() {
    return group.isPresent() && group.get().getType() != TextSecureGroup.Type.DELIVER;
  }
}
