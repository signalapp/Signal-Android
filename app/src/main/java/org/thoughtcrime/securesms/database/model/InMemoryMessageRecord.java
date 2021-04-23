package org.thoughtcrime.securesms.database.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;

/**
 * In memory message record for use in temporary conversation messages.
 */
public class InMemoryMessageRecord extends MessageRecord {

  private InMemoryMessageRecord(long id,
                                String body,
                                Recipient conversationRecipient,
                                long threadId,
                                long type)
  {
    super(id,
          body,
          conversationRecipient,
          conversationRecipient,
          1,
          System.currentTimeMillis(),
          System.currentTimeMillis(),
          System.currentTimeMillis(),
          threadId,
          0,
          0,
          type,
          Collections.emptyList(),
          Collections.emptyList(),
          -1,
          0,
          System.currentTimeMillis(),
          0,
          false,
          Collections.emptyList(),
          false,
          0,
          0);
  }

  @Override
  public boolean isMms() {
    return false;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  @Override
  public boolean isInMemoryMessageRecord() {
    return true;
  }

  public boolean showActionButton() {
    return false;
  }

  public @StringRes int getActionButtonText() {
    return 0;
  }

  /**
   * Warning message to show during message request state if you do not have groups in common
   * with an individual or do not know anyone in the group.
   */
  public static final class NoGroupsInCommon extends InMemoryMessageRecord {
    private final boolean isGroup;

    public NoGroupsInCommon(long threadId, boolean isGroup) {
      super(-1, "", Recipient.UNKNOWN, threadId, 0);
      this.isGroup = isGroup;
    }

    @Override
    public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context) {
      return UpdateDescription.staticDescription(context.getString(isGroup ? R.string.ConversationUpdateItem_no_contacts_in_this_group_review_requests_carefully
                                                                           : R.string.ConversationUpdateItem_no_groups_in_common_review_requests_carefully),
                                                 R.drawable.ic_update_info_16);
    }

    @Override
    public boolean isUpdate() {
      return true;
    }

    @Override
    public boolean showActionButton() {
      return true;
    }

    public boolean isGroup() {
      return isGroup;
    }

    @Override
    public @StringRes int getActionButtonText() {
      return R.string.ConversationUpdateItem_learn_more;
    }
  }
}
