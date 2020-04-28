package org.thoughtcrime.securesms.profiles.edit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.IOException;

class EditPushGroupProfileRepository implements EditProfileRepository {

  private static final String TAG = Log.tag(EditPushGroupProfileRepository.class);

  private final Context      context;
  private final GroupId.Push groupId;

  EditPushGroupProfileRepository(@NonNull Context context, @NonNull GroupId.Push groupId) {
    this.context = context.getApplicationContext();
    this.groupId = groupId;
  }

  @Override
  public void getCurrentProfileName(@NonNull Consumer<ProfileName> profileNameConsumer) {
    profileNameConsumer.accept(ProfileName.EMPTY);
  }

  @Override
  public void getCurrentAvatar(@NonNull Consumer<byte[]> avatarConsumer) {
    SimpleTask.run(() -> {
      final RecipientId recipientId = getRecipientId();

      if (AvatarHelper.hasAvatar(context, recipientId)) {
        try {
          return Util.readFully(AvatarHelper.getAvatar(context, recipientId));
        } catch (IOException e) {
          Log.w(TAG, e);
          return null;
        }
      } else {
        return null;
      }
    }, avatarConsumer::accept);
  }

  @Override
  public void getCurrentDisplayName(@NonNull Consumer<String> displayNameConsumer) {
    SimpleTask.run(() -> Recipient.resolved(getRecipientId()).getDisplayName(context), displayNameConsumer::accept);
  }

  @Override
  public void uploadProfile(@NonNull ProfileName profileName,
                            @NonNull String displayName,
                            @Nullable byte[] avatar,
                            @NonNull Consumer<UploadResult> uploadResultConsumer)
  {
    SimpleTask.run(() -> {
      try {
        GroupManager.updateGroup(context, groupId, avatar, displayName);

        return UploadResult.SUCCESS;
      } catch (InvalidNumberException e) {
        return UploadResult.ERROR_IO;
      }

    }, uploadResultConsumer::accept);
  }

  @Override
  public void getCurrentUsername(@NonNull Consumer<Optional<String>> callback) {
    callback.accept(Optional.absent());
  }

  @WorkerThread
  private RecipientId getRecipientId() {
    return DatabaseFactory.getRecipientDatabase(context).getByGroupId(groupId.toString())
                          .or(() -> {
                            throw new AssertionError("Recipient ID for Group ID does not exist.");
                          });
  }
}
