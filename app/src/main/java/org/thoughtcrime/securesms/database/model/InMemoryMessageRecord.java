package org.thoughtcrime.securesms.database.model;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ExpirationUtil;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * In memory message record for use in temporary conversation messages.
 */
public class InMemoryMessageRecord extends MessageRecord {

  private static final int NO_GROUPS_IN_COMMON_ID    = -1;
  private static final int UNIVERSAL_EXPIRE_TIMER_ID = -2;
  private static final int FORCE_BUBBLE_ID           = -3;
  private static final int HIDDEN_CONTACT_WARNING_ID = -4;

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
          Collections.emptySet(),
          Collections.emptySet(),
          -1,
          0,
          System.currentTimeMillis(),
          0,
          false,
          Collections.emptyList(),
          false,
          0,
          0,
          -1);
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
      super(NO_GROUPS_IN_COMMON_ID, "", Recipient.UNKNOWN, threadId, 0);
      this.isGroup = isGroup;
    }

    @Override
    public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
      return UpdateDescription.staticDescription(context.getString(isGroup ? R.string.ConversationUpdateItem_no_contacts_in_this_group_review_requests_carefully
                                                                           : R.string.ConversationUpdateItem_no_groups_in_common_review_requests_carefully),
                                                 R.drawable.symbol_info_compact_16);
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

  public static final class RemovedContactHidden extends InMemoryMessageRecord {

    public RemovedContactHidden(long threadId) {
      super(HIDDEN_CONTACT_WARNING_ID, "", Recipient.UNKNOWN, threadId, 0);
    }

    @Override
    public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
      return UpdateDescription.staticDescription(context.getString(R.string.ConversationUpdateItem_hidden_contact_message_to_add_back),
                                                 R.drawable.symbol_info_compact_16);
    }

    @Override
    public boolean isUpdate() {
      return true;
    }

    @Override
    public boolean showActionButton() {
      return false;
    }
  }

  /**
   * Show temporary update message about setting the disappearing messages timer upon first message
   * send.
   */
  public static final class UniversalExpireTimerUpdate extends InMemoryMessageRecord {

    public UniversalExpireTimerUpdate(long threadId) {
      super(UNIVERSAL_EXPIRE_TIMER_ID, "", Recipient.UNKNOWN, threadId, 0);
    }

    @Override
    public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
      String update = context.getString(R.string.ConversationUpdateItem_the_disappearing_message_time_will_be_set_to_s_when_you_message_them,
                                        ExpirationUtil.getExpirationDisplayValue(context, SignalStore.settings().getUniversalExpireTimer()));

      return UpdateDescription.staticDescription(update, R.drawable.symbol_timer_compact_24);
    }

    @Override
    public boolean isUpdate() {
      return true;
    }
  }

  /**
   * Useful for create an empty message record when one is needed.
   */
  public static final class ForceConversationBubble extends InMemoryMessageRecord {
    public ForceConversationBubble(Recipient conversationRecipient, long threadId) {
      super(FORCE_BUBBLE_ID, "", conversationRecipient, threadId, 0);
    }
  }
}
