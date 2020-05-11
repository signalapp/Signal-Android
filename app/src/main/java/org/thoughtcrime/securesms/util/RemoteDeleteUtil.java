package org.thoughtcrime.securesms.util;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public final class RemoteDeleteUtil {

  private static final long RECEIVE_THRESHOLD = TimeUnit.DAYS.toMillis(1);
  private static final long SEND_THRESHOLD    = TimeUnit.MINUTES.toMillis(30);

  private RemoteDeleteUtil() {}

  public static boolean isValidReceive(@NonNull MessageRecord targetMessage, @NonNull Recipient deleteSender, long deleteServerTimestamp) {
    boolean isValidSender = (deleteSender.isLocalNumber() && targetMessage.isOutgoing()) ||
                            (!deleteSender.isLocalNumber() && !targetMessage.isOutgoing());

    return isValidSender                                               &&
           targetMessage.getIndividualRecipient().equals(deleteSender) &&
           (deleteServerTimestamp - targetMessage.getServerTimestamp()) < RECEIVE_THRESHOLD;
  }

  public static boolean isValidSend(@NonNull Collection<MessageRecord> targetMessages, long currentTime) {
    // TODO [greyson] [remote-delete] Update with server timestamp when available for outgoing messages
    return Stream.of(targetMessages)
                 .allMatch(message -> message.isOutgoing()      &&
                                      !message.isRemoteDelete() &&
                                      !message.isPending()      &&
                                     (currentTime - message.getDateSent()) < SEND_THRESHOLD);
  }
}
