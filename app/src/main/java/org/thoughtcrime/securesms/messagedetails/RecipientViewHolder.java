package org.thoughtcrime.securesms.messagedetails;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.thoughtcrime.securesms.ConfirmIdentityDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

final class RecipientViewHolder extends RecyclerView.ViewHolder {
  private final AvatarImageView avatar;
  private final FromTextView    fromView;
  private final TextView        timestamp;
  private final TextView        error;
  private final View            conflictButton;
  private final View            unidentifiedDeliveryIcon;

  RecipientViewHolder(View itemView) {
    super(itemView);

    fromView                 = itemView.findViewById(R.id.recipient_name);
    avatar                   = itemView.findViewById(R.id.recipient_avatar);
    timestamp                = itemView.findViewById(R.id.recipient_timestamp);
    error                    = itemView.findViewById(R.id.error_description);
    conflictButton           = itemView.findViewById(R.id.conflict_button);
    unidentifiedDeliveryIcon = itemView.findViewById(R.id.ud_indicator);
  }

  void bind(RecipientDeliveryStatus data) {
    unidentifiedDeliveryIcon.setVisibility(TextSecurePreferences.isShowUnidentifiedDeliveryIndicatorsEnabled(itemView.getContext()) && data.isUnidentified() ? View.VISIBLE : View.GONE);
    fromView.setText(data.getRecipient());
    avatar.setRecipient(data.getRecipient());

    if (data.getKeyMismatchFailure() != null) {
      timestamp.setVisibility(View.GONE);
      error.setVisibility(View.VISIBLE);
      conflictButton.setVisibility(View.VISIBLE);
      error.setText(itemView.getContext().getString(R.string.MessageDetailsRecipient_new_safety_number));
      conflictButton.setOnClickListener(unused -> new ConfirmIdentityDialog(itemView.getContext(), data.getMessageRecord(), data.getKeyMismatchFailure()).show());
    } else if ((data.getNetworkFailure() != null && !data.getMessageRecord().isPending()) || (!data.getMessageRecord().getRecipient().isPushGroup() && data.getMessageRecord().isFailed())) {
      timestamp.setVisibility(View.GONE);
      error.setVisibility(View.VISIBLE);
      conflictButton.setVisibility(View.GONE);
      error.setText(itemView.getContext().getString(R.string.MessageDetailsRecipient_failed_to_send));
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
}
