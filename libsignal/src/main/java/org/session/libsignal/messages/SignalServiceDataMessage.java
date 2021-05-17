/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.messages;

import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.utilities.SignalServiceAddress;
import org.session.libsignal.protos.SignalServiceProtos.DataMessage.ClosedGroupControlMessage;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a decrypted Signal Service data message.
 */
public class SignalServiceDataMessage {
  private final long                                    timestamp;
  private final Optional<List<SignalServiceAttachment>> attachments;
  private final Optional<String>                        body;
  public  final Optional<SignalServiceGroup>            group;
  private final Optional<byte[]>                        profileKey;
  private final boolean                                 expirationUpdate;
  private final int                                     expiresInSeconds;
  private final Optional<Quote>                         quote;
  public  final Optional<List<SharedContact>>           contacts;
  private final Optional<List<Preview>>                 previews;
  // Loki
  private final Optional<ClosedGroupControlMessage>     closedGroupControlMessage;
  private final Optional<String>                        syncTarget;

  /**
   * Construct a SignalServiceDataMessage with a body and no attachments.
   *
   * @param timestamp The sent timestamp.
   * @param body The message contents.
   */
  public SignalServiceDataMessage(long timestamp, String body) {
    this(timestamp, body, 0);
  }

  /**
   * Construct an expiring SignalServiceDataMessage with a body and no attachments.
   *
   * @param timestamp The sent timestamp.
   * @param body The message contents.
   * @param expiresInSeconds The number of seconds in which the message should expire after having been seen.
   */
  public SignalServiceDataMessage(long timestamp, String body, int expiresInSeconds) {
    this(timestamp, (List<SignalServiceAttachment>)null, body, expiresInSeconds);
  }


  public SignalServiceDataMessage(final long timestamp, final SignalServiceAttachment attachment, final String body) {
    this(timestamp, new LinkedList<SignalServiceAttachment>() {{add(attachment);}}, body);
  }

  /**
   * Construct a SignalServiceDataMessage with a body and list of attachments.
   *
   * @param timestamp The sent timestamp.
   * @param attachments The attachments.
   * @param body The message contents.
   */
  public SignalServiceDataMessage(long timestamp, List<SignalServiceAttachment> attachments, String body) {
    this(timestamp, attachments, body, 0);
  }

  /**
   * Construct an expiring SignalServiceDataMessage with a body and list of attachments.
   *
   * @param timestamp The sent timestamp.
   * @param attachments The attachments.
   * @param body The message contents.
   * @param expiresInSeconds The number of seconds in which the message should expire after having been seen.
   */
  public SignalServiceDataMessage(long timestamp, List<SignalServiceAttachment> attachments, String body, int expiresInSeconds) {
    this(timestamp, null, attachments, body, expiresInSeconds);
  }

  /**
   * Construct a SignalServiceDataMessage group message with attachments and body.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information.
   * @param attachments The attachments.
   * @param body The message contents.
   */
  public SignalServiceDataMessage(long timestamp, SignalServiceGroup group, List<SignalServiceAttachment> attachments, String body) {
    this(timestamp, group, attachments, body, 0);
  }


  /**
   * Construct an expiring SignalServiceDataMessage group message with attachments and body.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information.
   * @param attachments The attachments.
   * @param body The message contents.
   * @param expiresInSeconds The number of seconds in which a message should disappear after having been seen.
   */
  public SignalServiceDataMessage(long timestamp, SignalServiceGroup group, List<SignalServiceAttachment> attachments, String body, int expiresInSeconds) {
    this(timestamp, group, attachments, body, expiresInSeconds, false, null, null, null, null);
  }

  /**
   * Construct a SignalServiceDataMessage.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information (or null if none).
   * @param attachments The attachments (or null if none).
   * @param body The message contents.
   * @param expiresInSeconds Number of seconds in which the message should disappear after being seen.
   */
  public SignalServiceDataMessage(long timestamp, SignalServiceGroup group,
                                  List<SignalServiceAttachment> attachments,
                                  String body, int expiresInSeconds,
                                  boolean expirationUpdate, byte[] profileKey,
                                  Quote quote, List<SharedContact> sharedContacts, List<Preview> previews)
  {
    this(timestamp, group, attachments, body, expiresInSeconds, expirationUpdate, profileKey, quote, sharedContacts, previews, null, null);
  }

  /**
   * Construct a SignalServiceDataMessage.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information (or null if none).
   * @param attachments The attachments (or null if none).
   * @param body The message contents.
   * @param expiresInSeconds Number of seconds in which the message should disappear after being seen.
   */
  public SignalServiceDataMessage(long timestamp, SignalServiceGroup group,
                                  List<SignalServiceAttachment> attachments,
                                  String body, int expiresInSeconds,
                                  boolean expirationUpdate, byte[] profileKey,
                                  Quote quote, List<SharedContact> sharedContacts, List<Preview> previews,
                                  ClosedGroupControlMessage closedGroupControlMessage,
                                  String syncTarget)
  {
    this.timestamp                   = timestamp;
    this.body                        = Optional.fromNullable(body);
    this.group                       = Optional.fromNullable(group);
    this.expiresInSeconds            = expiresInSeconds;
    this.expirationUpdate            = expirationUpdate;
    this.profileKey                  = Optional.fromNullable(profileKey);
    this.quote                       = Optional.fromNullable(quote);
    this.closedGroupControlMessage   = Optional.fromNullable(closedGroupControlMessage);
    this.syncTarget                  = Optional.fromNullable(syncTarget);

    if (attachments != null && !attachments.isEmpty()) {
      this.attachments = Optional.of(attachments);
    } else {
      this.attachments = Optional.absent();
    }

    if (sharedContacts != null && !sharedContacts.isEmpty()) {
      this.contacts = Optional.of(sharedContacts);
    } else {
      this.contacts = Optional.absent();
    }

    if (previews != null && !previews.isEmpty()) {
      this.previews = Optional.of(previews);
    } else {
      this.previews = Optional.absent();
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  /**
   * @return The message timestamp.
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * @return The message attachments (if any).
   */
  public Optional<List<SignalServiceAttachment>> getAttachments() {
    return attachments;
  }

  /**
   * @return The message body (if any).
   */
  public Optional<String> getBody() {
    return body;
  }

  /**
   * @return The message group info (if any).
   */
  public Optional<SignalServiceGroup> getGroupInfo() {
    return group;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public boolean isGroupMessage() {
      return group.isPresent();
  }

  public boolean isGroupUpdate() {
    return group.isPresent() && group.get().getType() != SignalServiceGroup.Type.DELIVER;
  }

  public int getExpiresInSeconds() { return expiresInSeconds; }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
  }

  public Optional<String> getSyncTarget() {
    return syncTarget;
  }

  public Optional<Quote> getQuote() {
    return quote;
  }

  public Optional<List<SharedContact>> getSharedContacts() {
    return contacts;
  }

  public Optional<List<Preview>> getPreviews() {
    return previews;
  }

  // Loki
  public Optional<ClosedGroupControlMessage> getClosedGroupControlMessage() { return closedGroupControlMessage; }

  public boolean hasVisibleContent() {
    return (body.isPresent() && !body.get().isEmpty())
        || (attachments.isPresent() && !attachments.get().isEmpty());
  }

  public int getTTL() {
    return 0;
  }

  public static class Builder {
    private List<SignalServiceAttachment> attachments    = new LinkedList<SignalServiceAttachment>();
    private List<SharedContact>           sharedContacts = new LinkedList<SharedContact>();
    private List<Preview>                 previews       = new LinkedList<Preview>();

    private long                 timestamp;
    private SignalServiceGroup   group;
    private String               body;
    private int                  expiresInSeconds;
    private boolean              expirationUpdate;
    private byte[]               profileKey;
    private Quote                quote;
    private String               syncTarget;

    private Builder() {}

    public Builder withTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder asGroupMessage(SignalServiceGroup group) {
      this.group = group;
      return this;
    }

    public Builder withAttachments(List<SignalServiceAttachment> attachments) {
      this.attachments.addAll(attachments);
      return this;
    }

    public Builder withBody(String body) {
      this.body = body;
      return this;
    }

    public Builder withSyncTarget(String syncTarget) {
      this.syncTarget = syncTarget;
      return this;
    }

    public Builder asExpirationUpdate(boolean expirationUpdate) {
      this.expirationUpdate = expirationUpdate;
      return this;
    }

    public Builder withExpiration(int expiresInSeconds) {
      this.expiresInSeconds = expiresInSeconds;
      return this;
    }

    public Builder withProfileKey(byte[] profileKey) {
      this.profileKey = profileKey;
      return this;
    }

    public Builder withQuote(Quote quote) {
      this.quote = quote;
      return this;
    }

    public Builder withSharedContacts(List<SharedContact> contacts) {
      this.sharedContacts.addAll(contacts);
      return this;
    }

    public Builder withPreviews(List<Preview> previews) {
      this.previews.addAll(previews);
      return this;
    }

    public SignalServiceDataMessage build() {
      if (timestamp == 0) timestamp = System.currentTimeMillis();
      // closedGroupUpdate is always null because we don't use SignalServiceDataMessage to send them (we use ClosedGroupUpdateMessageSendJob)
      return new SignalServiceDataMessage(timestamp, group, attachments, body, expiresInSeconds, expirationUpdate, profileKey, quote, sharedContacts, previews,
                                          null, syncTarget);
    }
  }

  public static class Quote {
    private final long                   id;
    private final SignalServiceAddress   author;
    private final String                 text;
    private final List<QuotedAttachment> attachments;

    public Quote(long id, SignalServiceAddress author, String text, List<QuotedAttachment> attachments) {
      this.id          = id;
      this.author      = author;
      this.text        = text;
      this.attachments = attachments;
    }

    public long getId() {
      return id;
    }

    public SignalServiceAddress getAuthor() {
      return author;
    }

    public String getText() {
      return text;
    }

    public List<QuotedAttachment> getAttachments() {
      return attachments;
    }

    public static class QuotedAttachment {
      private final String                  contentType;
      private final String                  fileName;
      private final SignalServiceAttachment thumbnail;

      public QuotedAttachment(String contentType, String fileName, SignalServiceAttachment thumbnail) {
        this.contentType = contentType;
        this.fileName    = fileName;
        this.thumbnail   = thumbnail;
      }

      public String getContentType() {
        return contentType;
      }

      public String getFileName() {
        return fileName;
      }

      public SignalServiceAttachment getThumbnail() {
        return thumbnail;
      }
    }
  }

  public static class Preview {
    private final String                            url;
    private final String                            title;
    private final Optional<SignalServiceAttachment> image;

    public Preview(String url, String title, Optional<SignalServiceAttachment> image) {
      this.url   = url;
      this.title = title;
      this.image = image;
    }

    public String getUrl() {
      return url;
    }

    public String getTitle() {
      return title;
    }

    public Optional<SignalServiceAttachment> getImage() {
      return image;
    }
  }
}
