package org.thoughtcrime.securesms.reactions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AvatarUtil;

import java.util.Collections;
import java.util.List;

final class ReactionRecipientsAdapter extends RecyclerView.Adapter<ReactionRecipientsAdapter.ViewHolder> {

  private List<Recipient> data = Collections.emptyList();

  public void updateData(List<Recipient> newData) {
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

  final class ViewHolder extends RecyclerView.ViewHolder {

    private final AvatarImageView avatar;
    private final TextView        recipient;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);

      avatar    = itemView.findViewById(R.id.reactions_bottom_view_recipient_avatar);
      recipient = itemView.findViewById(R.id.reactions_bottom_view_recipient_name);
    }

    void bind(Recipient recipient) {
      this.recipient.setText(recipient.getDisplayName(itemView.getContext()));

      if (recipient.equals(Recipient.self())) {
        AvatarUtil.loadIconIntoImageView(recipient, avatar);
      } else {
        this.avatar.setAvatar(GlideApp.with(avatar), recipient, false);
      }
    }
  }

}
