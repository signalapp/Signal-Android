package org.thoughtcrime.securesms.recipients.ui.bottomsheet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

final class RecipientDialogRepository {

  @NonNull  private final Context     context;
  @NonNull  private final RecipientId recipientId;
  @Nullable private final GroupId     groupId;

  RecipientDialogRepository(@NonNull Context context,
                            @NonNull RecipientId recipientId,
                            @Nullable GroupId groupId)
  {
    this.context     = context;
    this.recipientId = recipientId;
    this.groupId     = groupId;
  }

  @NonNull
  RecipientId getRecipientId() {
    return recipientId;
  }

  @Nullable
  GroupId getGroupId() {
    return groupId;
  }

  void getIdentity(@NonNull IdentityCallback callback) {
    SimpleTask.run(SignalExecutors.BOUNDED,
                   () -> DatabaseFactory.getIdentityDatabase(context)
                                        .getIdentity(recipientId)
                                        .orNull(),
                   callback::remoteIdentity);
  }

  void getRecipient(@NonNull RecipientCallback recipientCallback) {
    SimpleTask.run(SignalExecutors.BOUNDED,
                   () -> Recipient.resolved(recipientId),
                   recipientCallback::onRecipient);
  }

  interface IdentityCallback {
    void remoteIdentity(@Nullable IdentityDatabase.IdentityRecord identityRecord);
  }

  interface RecipientCallback {
    void onRecipient(@NonNull Recipient recipient);
  }
}
