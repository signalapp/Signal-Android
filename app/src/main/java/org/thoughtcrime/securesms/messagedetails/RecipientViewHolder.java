package org.thoughtcrime.securesms.messagedetails;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.transfercontrols.TransferControlView;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.List;
import java.util.Locale;

final class RecipientViewHolder extends RecyclerView.ViewHolder {
  private final AvatarImageView                 avatar;
  private final FromTextView                    fromView;
  private final TextView                        timestamp;
  private final TextView                        error;
  private final View                            conflictButton;
  private final View                            unidentifiedDeliveryIcon;
  private final BadgeImageView                  badge;
  private       MessageDetailsAdapter.Callbacks callbacks;

  RecipientViewHolder(@NonNull View itemView, @NonNull MessageDetailsAdapter.Callbacks callbacks) {
    super(itemView);

    this.callbacks = callbacks;

    fromView                 = itemView.findViewById(R.id.message_details_recipient_name);
    avatar                   = itemView.findViewById(R.id.message_details_recipient_avatar);
    timestamp                = itemView.findViewById(R.id.message_details_recipient_timestamp);
    error                    = itemView.findViewById(R.id.message_details_recipient_error_description);
    conflictButton           = itemView.findViewById(R.id.message_details_recipient_conflict_button);
    unidentifiedDeliveryIcon = itemView.findViewById(R.id.message_details_recipient_ud_indicator);
    badge                    = itemView.findViewById(R.id.message_details_recipient_badge);
  }

  void bind(RecipientDeliveryStatus data) {
    unidentifiedDeliveryIcon.setVisibility(TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(itemView.getContext()) && data.isUnidentified() ? View.VISIBLE : View.GONE);
    fromView.setText(data.getRecipient(), data.getRecipient().getDisplayName(itemView.getContext()), null, true, true);
    avatar.setRecipient(data.getRecipient());
    badge.setBadgeFromRecipient(data.getRecipient());

    if (data.getKeyMismatchFailure() != null) {
      timestamp.setVisibility(View.GONE);
      error.setVisibility(View.VISIBLE);
      conflictButton.setVisibility(View.VISIBLE);
      error.setText(itemView.getContext().getString(R.string.message_details_recipient__new_safety_number));
      conflictButton.setOnClickListener(unused -> callbacks.onErrorClicked(data.getMessageRecord()));
    } else if (isFailed(data)) {
      timestamp.setVisibility(View.GONE);
      error.setVisibility(View.VISIBLE);
      conflictButton.setVisibility(View.GONE);
      error.setText(itemView.getContext().getString(R.string.message_details_recipient__failed_to_send));
    } else {
      timestamp.setVisibility(View.VISIBLE);
      error.setVisibility(View.GONE);
      conflictButton.setVisibility(View.GONE);

      if (data.getTimestamp() > 0) {
        Locale dateLocale = Locale.getDefault();
        timestamp.setText(DateUtils.getTimeString(itemView.getContext(), dateLocale, data.getTimestamp()));
      } else {
        timestamp.setText("");
      }
    }
  }

  private boolean isFailed(RecipientDeliveryStatus data) {
    if (data.getDeliveryStatus() == RecipientDeliveryStatus.Status.PENDING) {
      return false;
    }

    MessageRecord messageRecord = data.getMessageRecord();
    if (messageRecord.isAttachmentInExpectedState(AttachmentTable.TRANSFER_PROGRESS_FAILED)) {
      return true;
    }

    return (data.getNetworkFailure() != null && !data.getMessageRecord().isPending()) || (!data.getMessageRecord().getToRecipient().isPushGroup() && data.getMessageRecord().isFailed());
  }
}
