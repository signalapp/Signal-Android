package org.thoughtcrime.securesms.insights;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.Collections;
import java.util.List;

final class InsightsInsecureRecipientsAdapter extends RecyclerView.Adapter<InsightsInsecureRecipientsAdapter.ViewHolder> {

  private List<Recipient> data = Collections.emptyList();

  private final Consumer<Recipient> onInviteClickedConsumer;

  InsightsInsecureRecipientsAdapter(Consumer<Recipient> onInviteClickedConsumer) {
    this.onInviteClickedConsumer = onInviteClickedConsumer;
  }

  public void updateData(List<Recipient> recipients) {
    List<Recipient> oldData = data;
    data = recipients;

    DiffUtil.calculateDiff(new DiffCallback(oldData, data)).dispatchUpdatesTo(this);
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.insights_dashboard_adapter_item, parent, false), this::handleInviteClicked);
  }

  private void handleInviteClicked(@NonNull Integer position) {
    onInviteClickedConsumer.accept(data.get(position));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    holder.bind(data.get(position));
  }

  @Override
  public int getItemCount() {
    return data.size();
  }

  static final class ViewHolder extends RecyclerView.ViewHolder {

    private AvatarImageView avatarImageView;
    private TextView        displayName;

    private ViewHolder(@NonNull View itemView, Consumer<Integer> onInviteClicked) {
      super(itemView);

      avatarImageView = itemView.findViewById(R.id.recipient_avatar);
      displayName     = itemView.findViewById(R.id.recipient_display_name);

      Button invite = itemView.findViewById(R.id.recipient_invite);
      invite.setOnClickListener(v -> {
        int adapterPosition = getAdapterPosition();

        if (adapterPosition == RecyclerView.NO_POSITION) return;

        onInviteClicked.accept(adapterPosition);
      });
    }

    private void bind(@NonNull Recipient recipient) {
      displayName.setText(recipient.getDisplayName(itemView.getContext()));
      avatarImageView.setAvatar(GlideApp.with(itemView), recipient, false);
    }
  }

  private static class DiffCallback extends DiffUtil.Callback {

    private final List<Recipient> oldData;
    private final List<Recipient> newData;

    private DiffCallback(@NonNull List<Recipient> oldData,
                         @NonNull List<Recipient> newData)
    {
      this.oldData = oldData;
      this.newData = newData;
    }

    @Override
    public int getOldListSize() {
      return oldData.size();
    }

    @Override
    public int getNewListSize() {
      return newData.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
      return oldData.get(oldItemPosition).getId() == newData.get(newItemPosition).getId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
      return oldData.get(oldItemPosition).equals(newData.get(newItemPosition));
    }
  }
}
