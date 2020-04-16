package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;

import java.util.Objects;

/**
 * Sometimes a message that is referencing another message can arrive out of order. In these cases,
 * we want to temporarily hold on (i.e. keep a memory cache) to these messages and apply them after
 * we receive the referenced message.
 */
public final class EarlyMessageCache {

  private final LRUCache<MessageId, SignalServiceContent> cache = new LRUCache<>(100);

  /**
   * @param targetSender        The sender of the message this message depends on.
   * @param targetSentTimestamp The sent timestamp of the message this message depends on.
   */
  public void store(@NonNull RecipientId targetSender, long targetSentTimestamp, @NonNull SignalServiceContent content) {
    cache.put(new MessageId(targetSender, targetSentTimestamp), content);
  }

  /**
   * Returns and removes any content that is dependent on the provided message id.
   * @param sender        The sender of the message in question.
   * @param sentTimestamp The sent timestamp of the message in question.
   */
  public Optional<SignalServiceContent> retrieve(@NonNull RecipientId sender, long sentTimestamp) {
    return Optional.fromNullable(cache.remove(new MessageId(sender, sentTimestamp)));
  }

  private static final class MessageId {
    private final RecipientId sender;
    private final long        sentTimestamp;

    private MessageId(@NonNull RecipientId sender, long sentTimestamp) {
      this.sender        = sender;
      this.sentTimestamp = sentTimestamp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MessageId messageId = (MessageId) o;
      return sentTimestamp == messageId.sentTimestamp &&
          Objects.equals(sender, messageId.sender);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sentTimestamp, sender);
    }
  }
}
