package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class RemoteDeleteUtil {

  private static final long RECEIVE_THRESHOLD = TimeUnit.DAYS.toMillis(1);
  private static final long SEND_THRESHOLD    = TimeUnit.HOURS.toMillis(3);

  private RemoteDeleteUtil() {}

  public static boolean isValidReceive(@NonNull MessageRecord targetMessage, @NonNull Recipient deleteSender, long deleteServerTimestamp) {
    return isValidReceive(targetMessage, deleteSender.getId(), deleteServerTimestamp);
  }

  public static boolean isValidReceive(@NonNull MessageRecord targetMessage, @NonNull RecipientId deleteSenderId, long deleteServerTimestamp) {
    boolean selfIsDeleteSender = isSelf(deleteSenderId);

    boolean isValidIncomingOutgoing = (selfIsDeleteSender && targetMessage.isOutgoing()) ||
                                      (!selfIsDeleteSender && !targetMessage.isOutgoing());

    boolean isValidSender = targetMessage.getIndividualRecipient().getId().equals(deleteSenderId) || selfIsDeleteSender && targetMessage.isOutgoing();

    long messageTimestamp = selfIsDeleteSender && targetMessage.isOutgoing() ? targetMessage.getDateSent()
                                                                                 : targetMessage.getServerTimestamp();

    return isValidIncomingOutgoing &&
           isValidSender &&
           (((deleteServerTimestamp - messageTimestamp) < RECEIVE_THRESHOLD) || (selfIsDeleteSender && targetMessage.isOutgoing()));
  }

  public static boolean isValidSend(@NonNull Collection<MessageRecord> targetMessages, long currentTime) {
    // TODO [greyson] [remote-delete] Update with server timestamp when available for outgoing messages
    return Stream.of(targetMessages).allMatch(message -> isValidSend(message, currentTime));
  }

  private static boolean isValidSend(MessageRecord message, long currentTime) {
    return !message.isUpdate() &&
           message.isOutgoing() &&
           message.isPush() &&
           (!message.getRecipient().isGroup() || message.getRecipient().isActiveGroup()) &&
           !message.isRemoteDelete() &&
           !MessageRecordUtil.hasGiftBadge(message) &&
           !message.isPaymentNotification() &&
           (((currentTime - message.getDateSent()) < SEND_THRESHOLD) || message.getRecipient().isSelf());
  }

  private static boolean isSelf(@NonNull RecipientId recipientId) {
    return Recipient.isSelfSet() && Recipient.self().getId().equals(recipientId);
  }
}
