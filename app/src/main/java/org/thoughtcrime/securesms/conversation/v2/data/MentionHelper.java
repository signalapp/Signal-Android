package org.thoughtcrime.securesms.conversation.v2.data;

import android.content.Context;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MentionHelper {

  private Collection<Long>         messageIds          = new LinkedList<>();
  private Map<Long, List<Mention>> messageIdToMentions = new HashMap<>();

  public void add(MessageRecord record) {
    if (record.isMms()) {
      messageIds.add(record.getId());
    }
  }

  public void fetchMentions(Context context) {
    messageIdToMentions = SignalDatabase.mentions().getMentionsForMessages(messageIds);
  }

  public @Nullable List<Mention> getMentions(long id) {
    return messageIdToMentions.get(id);
  }
}
