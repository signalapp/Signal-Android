package org.thoughtcrime.securesms.conversation.ui.error;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.AlwaysChangedDiffUtil;

final class SafetyNumberChangeAdapter extends ListAdapter<ChangedRecipient, SafetyNumberChangeAdapter.ViewHolder> {

  private final Callbacks callbacks;

  SafetyNumberChangeAdapter(@NonNull Callbacks callbacks) {
    super(new AlwaysChangedDiffUtil<>());
    this.callbacks = callbacks;
  }

  @Override
  public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.safety_number_change_recipient, parent, false));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    final ChangedRecipient changedRecipient = getItem(position);
    holder.bind(changedRecipient);
  }

  class ViewHolder extends RecyclerView.ViewHolder {

    final AvatarImageView avatar;
    final FromTextView    name;
    final TextView        subtitle;
    final View            viewButton;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);

      avatar     = itemView.findViewById(R.id.safety_number_change_recipient_avatar);
      name       = itemView.findViewById(R.id.safety_number_change_recipient_name);
      subtitle   = itemView.findViewById(R.id.safety_number_change_recipient_subtitle);
      viewButton = itemView.findViewById(R.id.safety_number_change_recipient_view);
    }

    void bind(@NonNull ChangedRecipient changedRecipient) {
      avatar.setRecipient(changedRecipient.getRecipient());
      name.setText(changedRecipient.getRecipient());

      if (changedRecipient.isUnverified() || changedRecipient.isVerified()) {
        subtitle.setText(R.string.safety_number_change_dialog__previous_verified);

        Drawable check = ContextCompat.getDrawable(itemView.getContext(), R.drawable.check);
        if (check != null) {
          check.setBounds(0, 0, ViewUtil.dpToPx(12), ViewUtil.dpToPx(12));
          subtitle.setCompoundDrawables(check, null, null, null);
        }
      } else if (changedRecipient.getRecipient().hasAUserSetDisplayName(itemView.getContext())) {
        subtitle.setText(changedRecipient.getRecipient().getE164().or(""));
        subtitle.setCompoundDrawables(null, null, null, null);
      } else {
        subtitle.setText("");
      }
      subtitle.setVisibility(TextUtils.isEmpty(subtitle.getText()) ?  View.GONE : View.VISIBLE);

      viewButton.setOnClickListener(view -> callbacks.onViewIdentityRecord(changedRecipient.getIdentityRecord()));
    }
  }

  interface Callbacks {
    void onViewIdentityRecord(@NonNull IdentityRecord identityRecord);
  }
}
