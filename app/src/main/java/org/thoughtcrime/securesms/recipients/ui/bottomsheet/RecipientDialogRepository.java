package org.thoughtcrime.securesms.recipients.ui.bottomsheet;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupInsufficientRightsException;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupNotAMemberException;
import org.thoughtcrime.securesms.groups.ui.GroupChangeErrorCallback;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.io.IOException;
import java.util.Objects;

final class RecipientDialogRepository {

  private static final String TAG = Log.tag(RecipientDialogRepository.class);

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

  @NonNull RecipientId getRecipientId() {
    return recipientId;
  }

  @Nullable GroupId getGroupId() {
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

  void getGroupName(@NonNull Consumer<String> stringConsumer) {
    SimpleTask.run(SignalExecutors.BOUNDED,
                   () -> DatabaseFactory.getGroupDatabase(context).requireGroup(Objects.requireNonNull(groupId)).getTitle(),
                   stringConsumer::accept);
  }

  void removeMember(@NonNull Consumer<Boolean> onComplete, @NonNull GroupChangeErrorCallback error) {
    SimpleTask.run(SignalExecutors.UNBOUNDED,
                   () -> {
                     try {
                       GroupManager.ejectFromGroup(context, Objects.requireNonNull(groupId).requireV2(), Recipient.resolved(recipientId));
                       return true;
                     } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
                       Log.w(TAG, e);
                       error.onError(GroupChangeFailureReason.NO_RIGHTS);
                     } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                       Log.w(TAG, e);
                       error.onError(GroupChangeFailureReason.OTHER);
                     }
                     return false;
                   },
                   onComplete::accept);
  }

  void setMemberAdmin(boolean admin, @NonNull Consumer<Boolean> onComplete, @NonNull GroupChangeErrorCallback error) {
    SimpleTask.run(SignalExecutors.UNBOUNDED,
                   () -> {
                     try {
                       GroupManager.setMemberAdmin(context, Objects.requireNonNull(groupId).requireV2(), recipientId, admin);
                       return true;
                     } catch (GroupInsufficientRightsException | GroupNotAMemberException e) {
                       Log.w(TAG, e);
                       error.onError(GroupChangeFailureReason.NO_RIGHTS);
                     } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                       Log.w(TAG, e);
                       error.onError(GroupChangeFailureReason.OTHER);
                     }
                     return false;
                   },
                   onComplete::accept);
  }

  interface IdentityCallback {
    void remoteIdentity(@Nullable IdentityDatabase.IdentityRecord identityRecord);
  }

  interface RecipientCallback {
    void onRecipient(@NonNull Recipient recipient);
  }
}
