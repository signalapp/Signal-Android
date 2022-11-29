package org.thoughtcrime.securesms.mms;

import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.LinkedList;

public class OutgoingExpirationUpdateMessage extends OutgoingSecureMediaMessage {

  public OutgoingExpirationUpdateMessage(Recipient recipient, long sentTimeMillis, long expiresIn) {
    super(recipient,
          "",
          new LinkedList<>(),
          sentTimeMillis,
          ThreadTable.DistributionTypes.CONVERSATION,
          expiresIn,
          false,
          StoryType.NONE,
          null,
          false,
          null,
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          null);
  }

  @Override
  public boolean isExpirationUpdate() {
    return true;
  }

  @Override
  public boolean isUrgent() {
    return false;
  }
}
