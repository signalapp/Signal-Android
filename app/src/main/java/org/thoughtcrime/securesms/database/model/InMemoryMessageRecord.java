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
                                Recipient author,
                                long threadId,
                                long type)
  {
    super(id,
          body,
          author,
          1,
          author,
          System.currentTimeMillis(),
          System.currentTimeMillis(),
          System.currentTimeMillis(),
          threadId,
          0,
          false,
          type,
          Collections.emptySet(),
          Collections.emptySet(),
          -1,
          0,
          System.currentTimeMillis(),
          1,
          false,
          false,
          Collections.emptyList(),
          false,
          0,
          false,
          -1,
          null,
          0,
          null);
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
    public ForceConversationBubble(Recipient author, long threadId) {
      super(FORCE_BUBBLE_ID, "", author, threadId, 0);
    }
  }
}
