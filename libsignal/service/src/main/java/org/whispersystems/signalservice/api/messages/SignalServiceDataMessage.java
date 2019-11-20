/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.LinkedList;
import java.util.List;

/**
 * Represents a decrypted Signal Service data message.
 */
public class SignalServiceDataMessage {

  private final long                                    timestamp;
  private final Optional<List<SignalServiceAttachment>> attachments;
  private final Optional<String>                        body;
  private final Optional<SignalServiceGroup>            group;
  private final Optional<byte[]>                        profileKey;
  private final boolean                                 endSession;
  private final boolean                                 expirationUpdate;
  private final int                                     expiresInSeconds;
  private final boolean                                 profileKeyUpdate;
  private final Optional<Quote>                         quote;
  private final Optional<List<SharedContact>>           contacts;
  private final Optional<List<Preview>>                 previews;
  private final Optional<Sticker>                       sticker;
  private final boolean                                 viewOnce;

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
    this(timestamp, group, attachments, body, false, expiresInSeconds, false, null, false, null, null, null, null, false);
  }

  /**
   * Construct a SignalServiceDataMessage.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information (or null if none).
   * @param attachments The attachments (or null if none).
   * @param body The message contents.
   * @param endSession Flag indicating whether this message should close a session.
   * @param expiresInSeconds Number of seconds in which the message should disappear after being seen.
   */
  public SignalServiceDataMessage(long timestamp, SignalServiceGroup group,
                                  List<SignalServiceAttachment> attachments,
                                  String body, boolean endSession, int expiresInSeconds,
                                  boolean expirationUpdate, byte[] profileKey, boolean profileKeyUpdate,
                                  Quote quote, List<SharedContact> sharedContacts, List<Preview> previews,
                                  Sticker sticker, boolean viewOnce)
  {
    this.timestamp             = timestamp;
    this.body                  = Optional.fromNullable(body);
    this.group                 = Optional.fromNullable(group);
    this.endSession            = endSession;
    this.expiresInSeconds      = expiresInSeconds;
    this.expirationUpdate      = expirationUpdate;
    this.profileKey            = Optional.fromNullable(profileKey);
    this.profileKeyUpdate      = profileKeyUpdate;
    this.quote                 = Optional.fromNullable(quote);
    this.sticker               = Optional.fromNullable(sticker);
    this.viewOnce              = viewOnce;

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

  public boolean isEndSession() {
    return endSession;
  }

  public boolean isExpirationUpdate() {
    return expirationUpdate;
  }

  public boolean isProfileKeyUpdate() {
    return profileKeyUpdate;
  }

  public boolean isGroupUpdate() {
    return group.isPresent() && group.get().getType() != SignalServiceGroup.Type.DELIVER;
  }

  public int getExpiresInSeconds() {
    return expiresInSeconds;
  }

  public Optional<byte[]> getProfileKey() {
    return profileKey;
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

  public Optional<Sticker> getSticker() {
    return sticker;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public static class Builder {

    private List<SignalServiceAttachment> attachments    = new LinkedList<>();
    private List<SharedContact>           sharedContacts = new LinkedList<>();
    private List<Preview>                 previews       = new LinkedList<>();

    private long               timestamp;
    private SignalServiceGroup group;
    private String             body;
    private boolean            endSession;
    private int                expiresInSeconds;
    private boolean            expirationUpdate;
    private byte[]             profileKey;
    private boolean            profileKeyUpdate;
    private Quote              quote;
    private Sticker            sticker;
    private boolean            viewOnce;

    private Builder() {}

    public Builder withTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder asGroupMessage(SignalServiceGroup group) {
      this.group = group;
      return this;
    }

    public Builder withAttachment(SignalServiceAttachment attachment) {
      this.attachments.add(attachment);
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

    public Builder asEndSessionMessage() {
      return asEndSessionMessage(true);
    }

    public Builder asEndSessionMessage(boolean endSession) {
      this.endSession = endSession;
      return this;
    }

    public Builder asExpirationUpdate() {
      return asExpirationUpdate(true);
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

    public Builder asProfileKeyUpdate(boolean profileKeyUpdate) {
      this.profileKeyUpdate = profileKeyUpdate;
      return this;
    }

    public Builder withQuote(Quote quote) {
      this.quote = quote;
      return this;
    }

    public Builder withSharedContact(SharedContact contact) {
      this.sharedContacts.add(contact);
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

    public Builder withSticker(Sticker sticker) {
      this.sticker = sticker;
      return this;
    }

    public Builder withViewOnce(boolean viewOnce) {
      this.viewOnce = viewOnce;
      return this;
    }

    public SignalServiceDataMessage build() {
      if (timestamp == 0) timestamp = System.currentTimeMillis();
      return new SignalServiceDataMessage(timestamp, group, attachments, body, endSession,
                                          expiresInSeconds, expirationUpdate, profileKey,
                                          profileKeyUpdate, quote, sharedContacts, previews,
                                          sticker, viewOnce);
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

  public static class Sticker {
    private final byte[]                  packId;
    private final byte[]                  packKey;
    private final int                     stickerId;
    private final SignalServiceAttachment attachment;

    public Sticker(byte[] packId, byte[] packKey, int stickerId, SignalServiceAttachment attachment) {
      this.packId     = packId;
      this.packKey    = packKey;
      this.stickerId  = stickerId;
      this.attachment = attachment;
    }

    public byte[] getPackId() {
      return packId;
    }

    public byte[] getPackKey() {
      return packKey;
    }

    public int getStickerId() {
      return stickerId;
    }

    public SignalServiceAttachment getAttachment() {
      return attachment;
    }
  }
}
