package org.thoughtcrime.securesms.conversation.v2.data;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class QuotedHelper {

  private Collection<MessageRecord> records          = new LinkedList<>();
  private Set<Long>                 hasBeenQuotedIds = new HashSet<>();

  public void add(MessageRecord record) {
    records.add(record);
  }

  public void fetchQuotedState() {
    hasBeenQuotedIds = SignalDatabase.messages().isQuoted(records);
  }

  public boolean isQuoted(long id) {
    return hasBeenQuotedIds.contains(id);
  }
}
