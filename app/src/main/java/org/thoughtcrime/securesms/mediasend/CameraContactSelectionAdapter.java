package org.thoughtcrime.securesms.mediasend;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.adapter.StableIdGenerator;

import java.util.ArrayList;
import java.util.List;

class CameraContactSelectionAdapter extends RecyclerView.Adapter<CameraContactSelectionAdapter.RecipientViewHolder> {

  private final List<Recipient>           recipients  = new ArrayList<>();
  private final StableIdGenerator<String> idGenerator = new StableIdGenerator<>();

  CameraContactSelectionAdapter() {
    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    return idGenerator.getId(recipients.get(position).getId().serialize());
  }

  @Override
  public @NonNull RecipientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new RecipientViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.camera_contact_selection_item, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull RecipientViewHolder holder, int position) {
    holder.bind(recipients.get(position), position == recipients.size() - 1);
  }

  @Override
  public int getItemCount() {
    return recipients.size();
  }

  void setRecipients(@NonNull List<Recipient> recipients) {
    this.recipients.clear();
    this.recipients.addAll(recipients);
    notifyDataSetChanged();
  }

  static class RecipientViewHolder extends RecyclerView.ViewHolder {

    private final FromTextView name;

    RecipientViewHolder(View itemView) {
      super(itemView);
      name = (FromTextView) itemView;
    }

    void bind(@NonNull Recipient recipient, boolean isLast) {
      name.setText(recipient, isLast ? null : ",");
    }
  }
}
