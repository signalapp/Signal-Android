package org.thoughtcrime.securesms.conversation.v2.data;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.util.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReactionHelper {

  private Collection<Long>                messageIds           = new LinkedList<>();
  private Map<Long, List<ReactionRecord>> messageIdToReactions = new HashMap<>();

  public void add(MessageRecord record) {
    messageIds.add(record.getId());
  }

  public void addAll(List<MessageRecord> records) {
    for (MessageRecord record : records) {
      add(record);
    }
  }

  public void fetchReactions() {
    messageIdToReactions = SignalDatabase.reactions().getReactionsForMessages(messageIds);
  }

  public @NonNull List<MessageRecord> buildUpdatedModels(@NonNull List<MessageRecord> records) {
    return records.stream()
                  .map(record -> {
                    List<ReactionRecord> reactions = messageIdToReactions.get(record.getId());

                    return recordWithReactions(record, reactions);
                  })
                  .collect(Collectors.toList());
  }

  public static @NonNull MessageRecord recordWithReactions(@NonNull MessageRecord record, List<ReactionRecord> reactions) {
    if (Util.hasItems(reactions)) {
      if (record instanceof MmsMessageRecord) {
        return ((MmsMessageRecord) record).withReactions(reactions);
      } else {
        throw new IllegalStateException("We have reactions for an unsupported record type: " + record.getClass().getName());
      }
    } else {
      return record;
    }
  }
}
