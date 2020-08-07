package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Collections;
import java.util.List;

final class MentionsPickerRepository {

  private final RecipientDatabase recipientDatabase;

  MentionsPickerRepository(@NonNull Context context) {
    recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
  }

  @WorkerThread
  @NonNull List<Recipient> search(@NonNull MentionQuery mentionQuery) {
    if (mentionQuery.query == null) {
      return Collections.emptyList();
    }

    List<RecipientId> recipientIds = Stream.of(mentionQuery.members)
                                           .filterNot(m -> m.getMember().isLocalNumber())
                                           .map(m -> m.getMember().getId())
                                           .toList();

    return recipientDatabase.queryRecipientsForMentions(mentionQuery.query, recipientIds);
  }

  static class MentionQuery {
    @Nullable private final String                            query;
    @NonNull  private final List<GroupMemberEntry.FullMember> members;

    MentionQuery(@Nullable String query, @NonNull List<GroupMemberEntry.FullMember> members) {
      this.query   = query;
      this.members = members;
    }
  }
}
