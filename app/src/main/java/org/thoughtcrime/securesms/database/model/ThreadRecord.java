/*
 * Copyright (C) 2012 Moxie Marlinspike
 * Copyright (C) 2013-2017 Open Whisper Systems
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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsTable;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.ThreadTable.Extra;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.Objects;

/**
 * Represents an entry in the {@link org.thoughtcrime.securesms.database.ThreadTable}.
 */
public final class ThreadRecord {

  private final long      threadId;
  private final String    body;
  private final Recipient recipient;
  private final long      type;
  private final long      date;
  private final long      deliveryStatus;
  private final int       deliveryReceiptCount;
  private final int       readReceiptCount;
  private final Uri       snippetUri;
  private final String    contentType;
  private final Extra     extra;
  private final boolean   meaningfulMessages;
  private final int       unreadCount;
  private final boolean   forcedUnread;
  private final int       distributionType;
  private final boolean   archived;
  private final long      expiresIn;
  private final long      lastSeen;
  private final boolean   isPinned;
  private final int       unreadSelfMentionsCount;

  private ThreadRecord(@NonNull Builder builder) {
    this.threadId                = builder.threadId;
    this.body                    = builder.body;
    this.recipient               = builder.recipient;
    this.date                    = builder.date;
    this.type                    = builder.type;
    this.deliveryStatus          = builder.deliveryStatus;
    this.deliveryReceiptCount    = builder.deliveryReceiptCount;
    this.readReceiptCount        = builder.readReceiptCount;
    this.snippetUri              = builder.snippetUri;
    this.contentType             = builder.contentType;
    this.extra                   = builder.extra;
    this.meaningfulMessages      = builder.meaningfulMessages;
    this.unreadCount             = builder.unreadCount;
    this.forcedUnread            = builder.forcedUnread;
    this.distributionType        = builder.distributionType;
    this.archived                = builder.archived;
    this.expiresIn               = builder.expiresIn;
    this.lastSeen                = builder.lastSeen;
    this.isPinned                = builder.isPinned;
    this.unreadSelfMentionsCount = builder.unreadSelfMentionsCount;
  }

  public long getThreadId() {
    return threadId;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @Nullable Uri getSnippetUri() {
    return snippetUri;
  }

  public @NonNull String getBody() {
    return body;
  }

  public @Nullable Extra getExtra() {
    return extra;
  }

  public @Nullable String getContentType() {
    return contentType;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public boolean isForcedUnread() {
    return forcedUnread;
  }

  public boolean isRead() {
    return unreadCount == 0 && !forcedUnread;
  }

  public long getDate() {
    return date;
  }

  public boolean isArchived() {
    return archived;
  }

  public long getType() {
    return type;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public boolean isOutgoing() {
    return MmsSmsColumns.Types.isOutgoingMessageType(type);
  }

  public boolean isOutgoingAudioCall() {
    return SmsTable.Types.isOutgoingAudioCall(type);
  }

  public boolean isOutgoingVideoCall() {
    return SmsTable.Types.isOutgoingVideoCall(type);
  }

  public boolean isVerificationStatusChange() {
    return StatusUtil.isVerificationStatusChange(type);
  }

  public boolean isPending() {
    return StatusUtil.isPending(type);
  }

  public boolean isFailed() {
    return StatusUtil.isFailed(type, deliveryStatus);
  }

  public boolean isRemoteRead() {
    return readReceiptCount > 0;
  }

  public boolean isPendingInsecureSmsFallback() {
    return SmsTable.Types.isPendingInsecureSmsFallbackType(type);
  }

  public boolean isDelivered() {
    return StatusUtil.isDelivered(deliveryStatus, deliveryReceiptCount);
  }

  public @Nullable RecipientId getGroupAddedBy() {
    if (extra != null && extra.getGroupAddedBy() != null) return RecipientId.from(extra.getGroupAddedBy());
    else                                                  return null;
  }

  public @NonNull RecipientId getIndividualRecipientId() {
    if (extra != null && extra.getIndividualRecipientId() != null) {
      return RecipientId.from(extra.getIndividualRecipientId());
    } else {
      if (getRecipient().isGroup()) {
        return RecipientId.UNKNOWN;
      } else {
        return getRecipient().getId();
      }
    }
  }

  public @NonNull RecipientId getGroupMessageSender() {
    RecipientId threadRecipientId     = getRecipient().getId();
    RecipientId individualRecipientId = getIndividualRecipientId();

    if (threadRecipientId.equals(individualRecipientId)) {
      return Recipient.self().getId();
    } else {
      return individualRecipientId;
    }
  }

  public boolean isMessageRequestAccepted() {
    if (extra != null) return extra.isMessageRequestAccepted();
    else               return true;
  }

  public boolean isPinned() {
    return isPinned;
  }

  public int getUnreadSelfMentionsCount() {
    return unreadSelfMentionsCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ThreadRecord that = (ThreadRecord) o;
    return threadId == that.threadId &&
           type == that.type &&
           date == that.date &&
           deliveryStatus == that.deliveryStatus &&
           deliveryReceiptCount == that.deliveryReceiptCount &&
           readReceiptCount == that.readReceiptCount &&
           meaningfulMessages == that.meaningfulMessages &&
           unreadCount == that.unreadCount &&
           forcedUnread == that.forcedUnread &&
           distributionType == that.distributionType &&
           archived == that.archived &&
           expiresIn == that.expiresIn &&
           lastSeen == that.lastSeen &&
           isPinned == that.isPinned &&
           body.equals(that.body) &&
           recipient.equals(that.recipient) &&
           unreadSelfMentionsCount == that.unreadSelfMentionsCount &&
           Objects.equals(snippetUri, that.snippetUri) &&
           Objects.equals(contentType, that.contentType) &&
           Objects.equals(extra, that.extra);
  }

  @Override
  public int hashCode() {
    return Objects.hash(threadId,
                        body,
                        recipient,
                        type,
                        date,
                        deliveryStatus,
                        deliveryReceiptCount,
                        readReceiptCount,
                        snippetUri,
                        contentType,
                        extra,
                        meaningfulMessages,
                        unreadCount,
                        forcedUnread,
                        distributionType,
                        archived,
                        expiresIn,
                        lastSeen,
                        isPinned,
                        unreadSelfMentionsCount);
  }

  public static class Builder {
    private long      threadId;
    private String    body;
    private Recipient recipient = Recipient.UNKNOWN;
    private long      type;
    private long      date;
    private long      deliveryStatus;
    private int       deliveryReceiptCount;
    private int       readReceiptCount;
    private Uri       snippetUri;
    private String    contentType;
    private Extra     extra;
    private boolean   meaningfulMessages;
    private int       unreadCount;
    private boolean   forcedUnread;
    private int       distributionType;
    private boolean   archived;
    private long      expiresIn;
    private long      lastSeen;
    private boolean   isPinned;
    private int       unreadSelfMentionsCount;

    public Builder(long threadId) {
      this.threadId = threadId;
    }

    public Builder setBody(@NonNull String body) {
      this.body = body;
      return this;
    }

    public Builder setRecipient(@NonNull Recipient recipient) {
      this.recipient = recipient;
      return this;
    }

    public Builder setType(long type) {
      this.type = type;
      return this;
    }

    public Builder setThreadId(long threadId) {
      this.threadId = threadId;
      return this;
    }

    public Builder setDate(long date) {
      this.date = date;
      return this;
    }

    public Builder setDeliveryStatus(long deliveryStatus) {
      this.deliveryStatus = deliveryStatus;
      return this;
    }

    public Builder setDeliveryReceiptCount(int deliveryReceiptCount) {
      this.deliveryReceiptCount = deliveryReceiptCount;
      return this;
    }

    public Builder setReadReceiptCount(int readReceiptCount) {
      this.readReceiptCount = readReceiptCount;
      return this;
    }

    public Builder setSnippetUri(@Nullable Uri snippetUri) {
      this.snippetUri = snippetUri;
      return this;
    }

    public Builder setContentType(@Nullable String contentType) {
      this.contentType = contentType;
      return this;
    }

    public Builder setExtra(@Nullable Extra extra) {
      this.extra = extra;
      return this;
    }

    public Builder setMeaningfulMessages(boolean meaningfulMessages) {
      this.meaningfulMessages = meaningfulMessages;
      return this;
    }

    public Builder setUnreadCount(int unreadCount) {
      this.unreadCount = unreadCount;
      return this;
    }

    public Builder setForcedUnread(boolean forcedUnread) {
      this.forcedUnread = forcedUnread;
      return this;
    }

    public Builder setDistributionType(int distributionType) {
      this.distributionType = distributionType;
      return this;
    }

    public Builder setArchived(boolean archived) {
      this.archived = archived;
      return this;
    }

    public Builder setExpiresIn(long expiresIn) {
      this.expiresIn = expiresIn;
      return this;
    }

    public Builder setLastSeen(long lastSeen) {
      this.lastSeen = lastSeen;
      return this;
    }

    public Builder setPinned(boolean isPinned) {
      this.isPinned = isPinned;
      return this;
    }

    public Builder setUnreadSelfMentionsCount(int unreadSelfMentionsCount) {
      this.unreadSelfMentionsCount = unreadSelfMentionsCount;
      return this;
    }

    public ThreadRecord build() {
      if (distributionType == ThreadTable.DistributionTypes.CONVERSATION) {
        Preconditions.checkArgument(threadId > 0);
        Preconditions.checkNotNull(body);
        Preconditions.checkNotNull(recipient);
      }
      return new ThreadRecord(this);
    }
  }
}
