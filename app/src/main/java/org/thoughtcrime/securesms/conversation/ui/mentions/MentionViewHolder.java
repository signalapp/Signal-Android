package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingAdapter;
import org.thoughtcrime.securesms.util.MappingViewHolder;

public class MentionViewHolder extends MappingViewHolder<MentionViewState> {

  private final AvatarImageView avatar;
  private final TextView        name;
  private final TextView        username;

  @Nullable private final MentionEventsListener mentionEventsListener;

  public MentionViewHolder(@NonNull View itemView, @Nullable MentionEventsListener mentionEventsListener) {
    super(itemView);
    this.mentionEventsListener = mentionEventsListener;

    avatar   = findViewById(R.id.mention_recipient_avatar);
    name     = findViewById(R.id.mention_recipient_name);
    username = findViewById(R.id.mention_recipient_username);
  }

  @Override
  public void bind(@NonNull MentionViewState model) {
    avatar.setRecipient(model.getRecipient());
    name.setText(model.getName(context));
    username.setText(model.getUsername());
    itemView.setOnClickListener(v -> {
      if (mentionEventsListener != null) {
        mentionEventsListener.onMentionClicked(model.getRecipient());
      }
    });
  }

  public interface MentionEventsListener {
    void onMentionClicked(@NonNull Recipient recipient);
  }

  public static MappingAdapter.Factory<MentionViewState> createFactory(@Nullable MentionEventsListener mentionEventsListener) {
    return new MappingAdapter.LayoutFactory<>(view -> new MentionViewHolder(view, mentionEventsListener), R.layout.mentions_picker_recipient_list_item);
  }
}
