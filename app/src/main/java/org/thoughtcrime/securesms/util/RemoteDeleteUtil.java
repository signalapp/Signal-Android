package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class RemoteDeleteUtil {

  private static final long RECEIVE_THRESHOLD = TimeUnit.DAYS.toMillis(0);
  private static final long SEND_THRESHOLD    = TimeUnit.DAYS.toMillis(1);

  private RemoteDeleteUtil() {}

  public static boolean isValidReceive(@NonNull MessageRecord targetMessage, @NonNull Recipient deleteSender, long deleteServerTimestamp) {
    boolean isValidIncomingOutgoing = (deleteSender.isSelf() && targetMessage.isOutgoing()) ||
                                      (!deleteSender.isSelf() && !targetMessage.isOutgoing());

    boolean isValidSender = targetMessage.getIndividualRecipient().equals(deleteSender) ||
                            deleteSender.isSelf() && targetMessage.isOutgoing();

    long messageTimestamp = deleteSender.isSelf() && targetMessage.isOutgoing() ? targetMessage.getDateSent()
                                                                                : targetMessage.getServerTimestamp();

    return isValidIncomingOutgoing &&
           isValidSender           &&
           (deleteServerTimestamp - messageTimestamp) < RECEIVE_THRESHOLD;
  }

  public static boolean isValidSend(@NonNull Collection<MessageRecord> targetMessages, long currentTime) {
    // TODO [greyson] [remote-delete] Update with server timestamp when available for outgoing messages
    return Stream.of(targetMessages).allMatch(message -> isValidSend(message, currentTime));
  }

  private static boolean isValidSend(MessageRecord message, long currentTime) {
    return !message.isUpdate()                                                           &&
           message.isOutgoing()                                                          &&
           message.isPush()                                                              &&
           (!message.getRecipient().isGroup() || message.getRecipient().isActiveGroup()) &&
           !message.getRecipient().isSelf()                                              &&
           !message.isRemoteDelete()                                                     &&
           !MessageRecordUtil.hasGiftBadge(message)                                      &&
           (currentTime - message.getDateSent()) < SEND_THRESHOLD;
  }
}
