package org.thoughtcrime.securesms.sharing;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

public class ShareSelectionMappingModel implements MappingModel<ShareSelectionMappingModel> {

  private final ShareContact shareContact;
  private final boolean      isFirst;

  public ShareSelectionMappingModel(@NonNull ShareContact shareContact, boolean isFirst) {
    this.shareContact = shareContact;
    this.isFirst      = isFirst;
  }

  @NonNull String getName(@NonNull Context context) {
    String name = shareContact.getRecipientId()
                              .transform(Recipient::resolved)
                              .transform(recipient -> recipient.isSelf() ? context.getString(R.string.note_to_self)
                                                                         : recipient.getShortDisplayNameIncludingUsername(context))
                              .or(shareContact::getNumber);

    return isFirst ? name : context.getString(R.string.ShareActivity__comma_s, name);
  }

  @Override
  public boolean areItemsTheSame(@NonNull ShareSelectionMappingModel newItem) {
    return newItem.shareContact.equals(shareContact);
  }

  @Override
  public boolean areContentsTheSame(@NonNull ShareSelectionMappingModel newItem) {
    return areItemsTheSame(newItem) && newItem.isFirst == isFirst;
  }
}
