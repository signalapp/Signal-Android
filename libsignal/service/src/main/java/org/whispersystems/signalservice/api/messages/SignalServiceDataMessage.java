/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.OptionalUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a decrypted Signal Service data message.
 */
public class SignalServiceDataMessage {

  private final long                                    timestamp;
  private final Optional<List<SignalServiceAttachment>> attachments;
  private final Optional<String>                        body;
  private final Optional<SignalServiceGroupContext>     group;
  private final Optional<byte[]>                        profileKey;
  private final boolean                                 endSession;
  private final boolean                                 expirationUpdate;
  private final int                                     expiresInSeconds;
  private final boolean                                 profileKeyUpdate;
  private final Optional<Quote>                         quote;
  private final Optional<List<SharedContact>>           contacts;
  private final Optional<List<Preview>>                 previews;
  private final Optional<List<Mention>>                 mentions;
  private final Optional<Sticker>                       sticker;
  private final boolean                                 viewOnce;
  private final Optional<Reaction>                      reaction;
  private final Optional<RemoteDelete>                  remoteDelete;
  private final Optional<GroupCallUpdate>               groupCallUpdate;

  /**
   * Construct a SignalServiceDataMessage.
   *
   * @param timestamp The sent timestamp.
   * @param group The group information (or null if none).
   * @param groupV2 The group information (or null if none).
   * @param attachments The attachments (or null if none).
   * @param body The message contents.
   * @param endSession Flag indicating whether this message should close a session.
   * @param expiresInSeconds Number of seconds in which the message should disappear after being seen.
   */
  SignalServiceDataMessage(long timestamp,
                           SignalServiceGroup group, SignalServiceGroupV2 groupV2,
                           List<SignalServiceAttachment> attachments,
                           String body, boolean endSession, int expiresInSeconds,
                           boolean expirationUpdate, byte[] profileKey, boolean profileKeyUpdate,
                           Quote quote, List<SharedContact> sharedContacts, List<Preview> previews,
                           List<Mention> mentions, Sticker sticker, boolean viewOnce, Reaction reaction, RemoteDelete remoteDelete,
                           GroupCallUpdate groupCallUpdate)
  {
    try {
      this.group = SignalServiceGroupContext.createOptional(group, groupV2);
    } catch (InvalidMessageException e) {
      throw new AssertionError(e);
    }

    this.timestamp        = timestamp;
    this.body             = OptionalUtil.absentIfEmpty(body);
    this.endSession       = endSession;
    this.expiresInSeconds = expiresInSeconds;
    this.expirationUpdate = expirationUpdate;
    this.profileKey       = Optional.fromNullable(profileKey);
    this.profileKeyUpdate = profileKeyUpdate;
    this.quote            = Optional.fromNullable(quote);
    this.sticker          = Optional.fromNullable(sticker);
    this.viewOnce         = viewOnce;
    this.reaction         = Optional.fromNullable(reaction);
    this.remoteDelete     = Optional.fromNullable(remoteDelete);
    this.groupCallUpdate  = Optional.fromNullable(groupCallUpdate);

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

    if (mentions != null && !mentions.isEmpty()) {
      this.mentions = Optional.of(mentions);
    } else {
      this.mentions = Optional.absent();
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
   * @return The message group context (if any).
   */
  public Optional<SignalServiceGroupContext> getGroupContext() {
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

  public boolean isGroupV1Update() {
    return group.isPresent() &&
           group.get().getGroupV1().isPresent() &&
           group.get().getGroupV1().get().getType() != SignalServiceGroup.Type.DELIVER;
  }

  public boolean isGroupV2Message() {
    return group.isPresent() &&
           group.get().getGroupV2().isPresent();
  }

  public boolean isGroupV2Update() {
    return isGroupV2Message() &&
           group.get().getGroupV2().get().hasSignedGroupChange() &&
           !hasRenderableContent();
  }

  public boolean isEmptyGroupV2Message() {
    return isGroupV2Message() && !isGroupV2Update() && !hasRenderableContent();
  }

  /** Contains some user data that affects the conversation */
  public boolean hasRenderableContent() {
    return attachments.isPresent()   ||
           body.isPresent()          ||
           quote.isPresent()         ||
           contacts.isPresent()      ||
           previews.isPresent()      ||
           mentions.isPresent()      ||
           sticker.isPresent()       ||
           reaction.isPresent()      ||
           remoteDelete.isPresent();
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

  public Optional<List<Mention>> getMentions() {
    return mentions;
  }

  public Optional<Sticker> getSticker() {
    return sticker;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public Optional<Reaction> getReaction() {
    return reaction;
  }

  public Optional<RemoteDelete> getRemoteDelete() {
    return remoteDelete;
  }

  public Optional<GroupCallUpdate> getGroupCallUpdate() {
    return groupCallUpdate;
  }

  public static class Builder {

    private List<SignalServiceAttachment> attachments    = new LinkedList<>();
    private List<SharedContact>           sharedContacts = new LinkedList<>();
    private List<Preview>                 previews       = new LinkedList<>();
    private List<Mention>                 mentions       = new LinkedList<>();

    private long                 timestamp;
    private SignalServiceGroup   group;
    private SignalServiceGroupV2 groupV2;
    private String               body;
    private boolean              endSession;
    private int                  expiresInSeconds;
    private boolean              expirationUpdate;
    private byte[]               profileKey;
    private boolean              profileKeyUpdate;
    private Quote                quote;
    private Sticker              sticker;
    private boolean              viewOnce;
    private Reaction             reaction;
    private RemoteDelete         remoteDelete;
    private GroupCallUpdate      groupCallUpdate;

    private Builder() {}

    public Builder withTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder asGroupMessage(SignalServiceGroup group) {
      if (this.groupV2 != null) {
        throw new AssertionError("Can not contain both V1 and V2 group contexts.");
      }
      this.group = group;
      return this;
    }

    public Builder asGroupMessage(SignalServiceGroupV2 group) {
      if (this.group != null) {
        throw new AssertionError("Can not contain both V1 and V2 group contexts.");
      }
      this.groupV2 = group;
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

    public Builder withMentions(List<Mention> mentions) {
      this.mentions.addAll(mentions);
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

    public Builder withReaction(Reaction reaction) {
      this.reaction = reaction;
      return this;
    }

    public Builder withRemoteDelete(RemoteDelete remoteDelete) {
      this.remoteDelete = remoteDelete;
      return this;
    }

    public Builder withGroupCallUpdate(GroupCallUpdate groupCallUpdate) {
      this.groupCallUpdate = groupCallUpdate;
      return this;
    }

    public SignalServiceDataMessage build() {
      if (timestamp == 0) timestamp = System.currentTimeMillis();
      return new SignalServiceDataMessage(timestamp, group, groupV2, attachments, body, endSession,
                                          expiresInSeconds, expirationUpdate, profileKey,
                                          profileKeyUpdate, quote, sharedContacts, previews,
                                          mentions, sticker, viewOnce, reaction, remoteDelete,
                                          groupCallUpdate);
    }
  }

  public static class Quote {
    private final long                   id;
    private final SignalServiceAddress   author;
    private final String                 text;
    private final List<QuotedAttachment> attachments;
    private final List<Mention>          mentions;

    public Quote(long id, SignalServiceAddress author, String text, List<QuotedAttachment> attachments, List<Mention> mentions) {
      this.id          = id;
      this.author      = author;
      this.text        = text;
      this.attachments = attachments;
      this.mentions    = mentions;
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

    public List<Mention> getMentions() {
      return mentions;
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
    private final String                            description;
    private final long                              date;
    private final Optional<SignalServiceAttachment> image;

    public Preview(String url, String title, String description, long date, Optional<SignalServiceAttachment> image) {
      this.url         = url;
      this.title       = title;
      this.description = description;
      this.date        = date;
      this.image       = image;
    }

    public String getUrl() {
      return url;
    }

    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public long getDate() {
      return date;
    }

    public Optional<SignalServiceAttachment> getImage() {
      return image;
    }
  }

  public static class Sticker {
    private final byte[]                  packId;
    private final byte[]                  packKey;
    private final int                     stickerId;
    private final String                  emoji;
    private final SignalServiceAttachment attachment;

    public Sticker(byte[] packId, byte[] packKey, int stickerId, String emoji, SignalServiceAttachment attachment) {
      this.packId     = packId;
      this.packKey    = packKey;
      this.stickerId  = stickerId;
      this.emoji      = emoji;
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

    public String getEmoji() {
      return emoji;
    }

    public SignalServiceAttachment getAttachment() {
      return attachment;
    }
  }

  public static class Reaction {
    private final String               emoji;
    private final boolean              remove;
    private final SignalServiceAddress targetAuthor;
    private final long                 targetSentTimestamp;

    public Reaction(String emoji, boolean remove, SignalServiceAddress targetAuthor, long targetSentTimestamp) {
      this.emoji               = emoji;
      this.remove              = remove;
      this.targetAuthor        = targetAuthor;
      this.targetSentTimestamp = targetSentTimestamp;
    }

    public String getEmoji() {
      return emoji;
    }

    public boolean isRemove() {
      return remove;
    }

    public SignalServiceAddress getTargetAuthor() {
      return targetAuthor;
    }

    public long getTargetSentTimestamp() {
      return targetSentTimestamp;
    }
  }

  public static class RemoteDelete {
    private final long targetSentTimestamp;

    public RemoteDelete(long targetSentTimestamp) {
      this.targetSentTimestamp = targetSentTimestamp;
    }

    public long getTargetSentTimestamp() {
      return targetSentTimestamp;
    }
  }

  public static class Mention {
    private final UUID uuid;
    private final int  start;
    private final int  length;

    public Mention(UUID uuid, int start, int length) {
      this.uuid   = uuid;
      this.start  = start;
      this.length = length;
    }

    public UUID getUuid() {
      return uuid;
    }

    public int getStart() {
      return start;
    }

    public int getLength() {
      return length;
    }
  }

  public static class GroupCallUpdate {
    private final String eraId;

    public GroupCallUpdate(String eraId) {
      this.eraId = eraId;
    }

    public String getEraId() {
      return eraId;
    }
  }
}
