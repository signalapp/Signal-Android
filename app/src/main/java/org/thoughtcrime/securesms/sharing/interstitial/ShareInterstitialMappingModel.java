package org.thoughtcrime.securesms.sharing.interstitial;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.viewholders.RecipientMappingModel;

class ShareInterstitialMappingModel extends RecipientMappingModel<ShareInterstitialMappingModel> {

  private final Recipient recipient;
  private final boolean   isFirst;

  ShareInterstitialMappingModel(@NonNull Recipient recipient, boolean isFirst) {
    this.recipient = recipient;
    this.isFirst   = isFirst;
  }

  @Override
  public @NonNull String getName(@NonNull Context context) {
    String name = recipient.isSelf() ? context.getString(R.string.note_to_self)
                                     : recipient.getShortDisplayName(context);

    return isFirst ? name : context.getString(R.string.ShareActivity__comma_s, name);
  }

  @Override
  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  @Override
  public boolean areContentsTheSame(@NonNull ShareInterstitialMappingModel newItem) {
    return super.areContentsTheSame(newItem) && isFirst == newItem.isFirst;
  }
}
