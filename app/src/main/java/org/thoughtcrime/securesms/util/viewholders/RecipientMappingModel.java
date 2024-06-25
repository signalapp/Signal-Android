package org.thoughtcrime.securesms.util.viewholders;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.Objects;

public abstract class RecipientMappingModel<T extends RecipientMappingModel<T>> implements MappingModel<T> {

  public abstract @NonNull Recipient getRecipient();

  public @NonNull String getName(@NonNull Context context) {
    return getRecipient().getDisplayName(context);
  }

  @Override
  public boolean areItemsTheSame(@NonNull T newItem) {
    return getRecipient().getId().equals(newItem.getRecipient().getId());
  }

  @Override
  public boolean areContentsTheSame(@NonNull T newItem) {
    Context context = AppDependencies.getApplication();
    return getName(context).equals(newItem.getName(context)) && Objects.equals(getRecipient().getContactPhoto(), newItem.getRecipient().getContactPhoto());
  }

  public static class RecipientIdMappingModel extends RecipientMappingModel<RecipientIdMappingModel> {

    private final RecipientId recipientId;

    public RecipientIdMappingModel(@NonNull RecipientId recipientId) {
      this.recipientId = recipientId;
    }

    @Override
    public @NonNull Recipient getRecipient() {
      return Recipient.resolved(recipientId);
    }
  }
}
