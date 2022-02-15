package org.thoughtcrime.securesms.conversationlist;


import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.GlideRequests;

import java.util.Locale;
import java.util.Set;

public class ConversationListItemInboxZero extends LinearLayout implements BindableConversationListItem {
  public ConversationListItemInboxZero(Context context) {
    super(context);
  }

  public ConversationListItemInboxZero(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public ConversationListItemInboxZero(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public ConversationListItemInboxZero(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public void unbind() {

  }

  @Override
  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull ConversationSet selectedConversations)
  {

  }

  @Override
  public void setSelectedConversations(@NonNull ConversationSet conversations) {

  }

  @Override
  public void updateTypingIndicator(@NonNull Set<Long> typingThreads) {

  }
}
