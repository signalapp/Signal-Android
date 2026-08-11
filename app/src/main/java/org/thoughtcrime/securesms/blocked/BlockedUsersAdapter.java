package org.thoughtcrime.securesms.blocked;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;

import java.util.Locale;
import java.util.Objects;

final class BlockedUsersAdapter extends ListAdapter<Recipient, BlockedUsersAdapter.ViewHolder> {

  private final RecipientClickedListener recipientClickedListener;

  BlockedUsersAdapter(@NonNull RecipientClickedListener recipientClickedListener) {
    super(new RecipientDiffCallback());

    this.recipientClickedListener = recipientClickedListener;
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.blocked_users_adapter_item, parent, false),
                          position -> recipientClickedListener.onRecipientClicked(Objects.requireNonNull(getItem(position))));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(Objects.requireNonNull(getItem(position)));
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    private final AvatarImageView avatar;
    private final TextView        displayName;
    private final TextView        username;
    private final TextView        blockedAt;

    public ViewHolder(@NonNull View itemView, Consumer<Integer> clickConsumer) {
      super(itemView);

      this.avatar           = itemView.findViewById(R.id.avatar);
      this.displayName      = itemView.findViewById(R.id.display_name);
      this.username         = itemView.findViewById(R.id.username);
      this.blockedAt        = itemView.findViewById(R.id.blocked_at);

      itemView.setOnClickListener(unused -> {
        if (getAdapterPosition() != RecyclerView.NO_POSITION) {
          clickConsumer.accept(getAdapterPosition());
        }
      });
    }

    public void bind(@NonNull Recipient recipient) {
      avatar.setAvatar(recipient);
      displayName.setText(recipient.getDisplayName(itemView.getContext()));

      String identifier = recipient.getUsername().orElse(null);

      if (identifier != null) {
        username.setText(identifier);
        username.setVisibility(View.VISIBLE);
      } else {
        username.setVisibility(View.GONE);
      }

      long blockedAtTimestamp = recipient.getBlockedAt();
      if (blockedAtTimestamp > 0) {
        String time = DateUtils.formatDateWithYear(Locale.getDefault(), blockedAtTimestamp);
        blockedAt.setText(itemView.getContext().getString(R.string.BlockedUsersActivity__blocked_on_s, time));
        blockedAt.setVisibility(View.VISIBLE);
      } else {
        blockedAt.setVisibility(View.GONE);
      }
    }
  }

  private static final class RecipientDiffCallback extends DiffUtil.ItemCallback<Recipient> {

    @Override
    public boolean areItemsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }

    @Override
    public boolean areContentsTheSame(@NonNull Recipient oldItem, @NonNull Recipient newItem) {
      return oldItem.equals(newItem);
    }
  }

  interface RecipientClickedListener {
    void onRecipientClicked(@NonNull Recipient recipient);
  }
}
