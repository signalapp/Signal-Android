package org.thoughtcrime.securesms.conversation.ui.mentions;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.MappingModel;
import org.thoughtcrime.securesms.util.Util;

import java.util.Objects;

public final class MentionViewState implements MappingModel<MentionViewState> {

  private final Recipient recipient;

  public MentionViewState(@NonNull Recipient recipient) {
    this.recipient = recipient;
  }

  @NonNull String getName(@NonNull Context context) {
    return recipient.getDisplayName(context);
  }

  @NonNull Recipient getRecipient() {
    return recipient;
  }

  @NonNull String getUsername() {
    return Util.emptyIfNull(recipient.getDisplayUsername());
  }

  @Override
  public boolean areItemsTheSame(@NonNull MentionViewState newItem) {
    return recipient.getId().equals(newItem.recipient.getId());
  }

  @Override
  public boolean areContentsTheSame(@NonNull MentionViewState newItem) {
    Context context = ApplicationDependencies.getApplication();
    return recipient.getDisplayName(context).equals(newItem.recipient.getDisplayName(context)) &&
           Objects.equals(recipient.getProfileAvatar(), newItem.recipient.getProfileAvatar());
  }
}
