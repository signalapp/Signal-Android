package org.thoughtcrime.securesms.conversation.ui.mentions;

import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.conversation.ui.mentions.MentionViewHolder.MentionEventsListener;
import org.thoughtcrime.securesms.util.MappingAdapter;

public class MentionsPickerAdapter extends MappingAdapter {
  public MentionsPickerAdapter(@Nullable MentionEventsListener mentionEventsListener) {
    registerFactory(MentionViewState.class, MentionViewHolder.createFactory(mentionEventsListener));
  }
}
