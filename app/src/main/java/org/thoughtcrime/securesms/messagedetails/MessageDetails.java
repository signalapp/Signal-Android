package org.thoughtcrime.securesms.messagedetails;

import androidx.annotation.NonNull;

import com.annimon.stream.ComparatorCompat;

import org.thoughtcrime.securesms.conversation.ConversationMessage;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public final class MessageDetails {
  private static final Comparator<RecipientDeliveryStatus> HAS_DISPLAY_NAME     = (r1, r2) -> Boolean.compare(r2.getRecipient().hasAUserSetDisplayName(ApplicationDependencies.getApplication()), r1.getRecipient().hasAUserSetDisplayName(ApplicationDependencies.getApplication()));
  private static final Comparator<RecipientDeliveryStatus> ALPHABETICAL         = (r1, r2) -> r1.getRecipient().getDisplayName(ApplicationDependencies.getApplication()).compareToIgnoreCase(r2.getRecipient().getDisplayName(ApplicationDependencies.getApplication()));
  private static final Comparator<RecipientDeliveryStatus> RECIPIENT_COMPARATOR = ComparatorCompat.chain(HAS_DISPLAY_NAME).thenComparing(ALPHABETICAL);

  private final ConversationMessage conversationMessage;

  private final Collection<RecipientDeliveryStatus> pending;
  private final Collection<RecipientDeliveryStatus> sent;
  private final Collection<RecipientDeliveryStatus> delivered;
  private final Collection<RecipientDeliveryStatus> read;
  private final Collection<RecipientDeliveryStatus> notSent;
  private final Collection<RecipientDeliveryStatus> viewed;
  private final Collection<RecipientDeliveryStatus> skipped;

  MessageDetails(@NonNull ConversationMessage conversationMessage, @NonNull List<RecipientDeliveryStatus> recipients) {
    this.conversationMessage = conversationMessage;

    pending   = new TreeSet<>(RECIPIENT_COMPARATOR);
    sent      = new TreeSet<>(RECIPIENT_COMPARATOR);
    delivered = new TreeSet<>(RECIPIENT_COMPARATOR);
    read      = new TreeSet<>(RECIPIENT_COMPARATOR);
    notSent   = new TreeSet<>(RECIPIENT_COMPARATOR);
    viewed    = new TreeSet<>(RECIPIENT_COMPARATOR);
    skipped   = new TreeSet<>(RECIPIENT_COMPARATOR);

    if (conversationMessage.getMessageRecord().getRecipient().isSelf()) {
      read.addAll(recipients);
    } else if (conversationMessage.getMessageRecord().isOutgoing()) {
      for (RecipientDeliveryStatus status : recipients) {
        switch (status.getDeliveryStatus()) {
          case UNKNOWN:
            notSent.add(status);
            break;
          case PENDING:
            pending.add(status);
            break;
          case SENT:
            sent.add(status);
            break;
          case DELIVERED:
            delivered.add(status);
            break;
          case READ:
            read.add(status);
            break;
          case VIEWED:
            viewed.add(status);
            break;
          case SKIPPED:
            skipped.add(status);
            break;
        }
      }
    } else {
      sent.addAll(recipients);
    }
  }

  public @NonNull ConversationMessage getConversationMessage() {
    return conversationMessage;
  }

  public @NonNull Collection<RecipientDeliveryStatus> getPending() {
    return pending;
  }

  public @NonNull Collection<RecipientDeliveryStatus> getSent() {
    return sent;
  }

  public @NonNull Collection<RecipientDeliveryStatus> getSkipped() {return  skipped;}

  public @NonNull Collection<RecipientDeliveryStatus> getDelivered() {
    return delivered;
  }

  public @NonNull Collection<RecipientDeliveryStatus> getRead() {
    return read;
  }

  public @NonNull Collection<RecipientDeliveryStatus> getNotSent() {
    return notSent;
  }

  public @NonNull Collection<RecipientDeliveryStatus> getViewed() {
    return viewed;
  }
}
