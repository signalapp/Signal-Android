package org.thoughtcrime.securesms.conversation.v2.data;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.CallTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CallHelper {
  private final Collection<Long>          messageIds      = new LinkedList<>();
  private       Map<Long, CallTable.Call> messageIdToCall = Collections.emptyMap();

  public void add(MessageRecord messageRecord) {
    if (messageRecord.isCallLog() && !messageRecord.isGroupCall()) {
      messageIds.add(messageRecord.getId());
    }
  }

  public void fetchCalls() {
    if (!messageIds.isEmpty()) {
      messageIdToCall = SignalDatabase.calls().getCalls(messageIds);
    }
  }

  public @NonNull List<MessageRecord> buildUpdatedModels(@NonNull List<MessageRecord> records) {
    return records.stream()
                  .map(record -> {
                    if (record.isCallLog() && record instanceof MediaMmsMessageRecord) {
                      CallTable.Call call = messageIdToCall.get(record.getId());
                      if (call != null) {
                        return ((MediaMmsMessageRecord) record).withCall(call);
                      }
                    }
                    return record;
                  })
                  .collect(Collectors.toList());
  }
}
