package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.reactions.ReactionsLoader.Reaction;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AvatarUtil;

import java.util.Collections;
import java.util.List;

final class ReactionRecipientsAdapter extends RecyclerView.Adapter<ReactionRecipientsAdapter.ViewHolder> {

  private List<Reaction> data = Collections.emptyList();

  public void updateData(List<Reaction> newData) {
    data = newData;
    notifyDataSetChanged();
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext())
                                        .inflate(R.layout.reactions_bottom_sheet_dialog_fragment_recipient_item,
                                                 parent,
                                                 false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(data.get(position));
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  final static class ViewHolder extends RecyclerView.ViewHolder {

    private final AvatarImageView avatar;
    private final TextView        recipient;
    private final TextView        emoji;

    private ViewHolder(@NonNull View itemView) {
      super(itemView);

      avatar    = itemView.findViewById(R.id.reactions_bottom_view_recipient_avatar);
      recipient = itemView.findViewById(R.id.reactions_bottom_view_recipient_name);
      emoji     = itemView.findViewById(R.id.reactions_bottom_view_recipient_emoji);
    }

    void bind(@NonNull Reaction reaction) {
      this.recipient.setText(getReactionSenderDisplayName(itemView.getContext(), reaction.getSender()));
      this.emoji.setText(reaction.getEmoji());

      if (reaction.getSender().isLocalNumber()) {
        this.avatar.setAvatar(GlideApp.with(avatar), null, false);
        AvatarUtil.loadIconIntoImageView(reaction.getSender(), avatar);
      } else {
        this.avatar.setAvatar(GlideApp.with(avatar), reaction.getSender(), false);
      }
    }

    private static @NonNull String getReactionSenderDisplayName(@NonNull Context context, @NonNull Recipient sender) {
      String displayName = sender.getDisplayName(context);

      if (sender.isLocalNumber()) {
        return context.getString(R.string.ReactionsBottomSheetDialogFragment_you, displayName);
      } else {
        return displayName;
      }
    }
  }

}
