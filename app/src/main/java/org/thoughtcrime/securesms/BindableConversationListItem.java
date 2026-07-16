package org.thoughtcrime.securesms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.bumptech.glide.RequestManager;

import org.thoughtcrime.securesms.conversationlist.model.ConversationSet;
import org.thoughtcrime.securesms.database.model.ThreadWithRecipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  void bind(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull ThreadWithRecipient thread,
            @NonNull RequestManager requestManager, @NonNull Locale locale,
            @NonNull Set<Long> typingThreads,
            @NonNull ConversationSet selectedConversations,
            @Nullable RecipientId activeRecipientId);

  void setSelectedConversations(@NonNull ConversationSet conversations);
  void setActiveRecipientId(@Nullable RecipientId activeRecipientId);
  void updateTypingIndicator(@NonNull Set<Long> typingThreads);
  void updateTimestamp();
}
