package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

public final class AvatarGroupsV2DownloadJob extends BaseJob {

  public static final String KEY = "AvatarGroupsV2DownloadJob";

  private static final String TAG = Log.tag(AvatarGroupsV2DownloadJob.class);

  private static final long AVATAR_DOWNLOAD_FAIL_SAFE_MAX_SIZE = ByteUnit.MEGABYTES.toBytes(5);

  private static final String KEY_GROUP_ID = "group_id";
  private static final String CDN_KEY      = "cdn_key";

  private final GroupId.V2 groupId;
  private final String     cdnKey;

  public AvatarGroupsV2DownloadJob(@NonNull GroupId.V2 groupId, @NonNull String cdnKey) {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setQueue("AvatarGroupsV2DownloadJob::" + groupId)
                       .setMaxAttempts(10)
                       .build(),
         groupId,
         cdnKey);
  }

  private AvatarGroupsV2DownloadJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId, @NonNull String cdnKey) {
    super(parameters);
    this.groupId = groupId;
    this.cdnKey  = cdnKey;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(KEY_GROUP_ID, groupId.toString())
                   .putString(CDN_KEY, cdnKey)
                   .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    GroupTable            database = SignalDatabase.groups();
    Optional<GroupRecord> record   = database.getGroup(groupId);
    File                  attachment = null;

    try {
      if (!record.isPresent()) {
        Log.w(TAG, "Cannot download avatar for unknown group");
        return;
      }

      if (cdnKey.length() == 0) {
        Log.w(TAG, "Removing avatar for group " + groupId);
        AvatarHelper.setAvatar(context, record.get().getRecipientId(), null);
        database.onAvatarUpdated(groupId, false);
        return;
      }

      Log.i(TAG, "Downloading new avatar for group " + groupId);
      byte[] decryptedAvatar = downloadGroupAvatarBytes(context, record.get().requireV2GroupProperties().getGroupMasterKey(), cdnKey);

      AvatarHelper.setAvatar(context, record.get().getRecipientId(), decryptedAvatar != null ? new ByteArrayInputStream(decryptedAvatar) : null);
      database.onAvatarUpdated(groupId, true);

    } catch (NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    }
  }

  public static @Nullable byte[] downloadGroupAvatarBytes(@NonNull Context context,
                                                          @NonNull GroupMasterKey groupMasterKey,
                                                          @NonNull String cdnKey)
      throws IOException
  {
    if (cdnKey.length() == 0) {
      return null;
    }

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    File              attachment        = File.createTempFile("avatar", "gv2", context.getCacheDir());
    attachment.deleteOnExit();

    SignalServiceMessageReceiver receiver      = AppDependencies.getSignalServiceMessageReceiver();
    byte[]                       encryptedData;

    try (FileInputStream inputStream = receiver.retrieveGroupsV2ProfileAvatar(cdnKey, attachment, AVATAR_DOWNLOAD_FAIL_SAFE_MAX_SIZE)) {
      encryptedData = new byte[(int) attachment.length()];

      StreamUtil.readFully(inputStream, encryptedData);

      GroupsV2Operations                 operations      = AppDependencies.getGroupsV2Operations();
      GroupsV2Operations.GroupOperations groupOperations = operations.forGroup(groupSecretParams);

      return groupOperations.decryptAvatar(encryptedData);
    } finally {
      if (attachment.exists())
        if (!attachment.delete()) {
          Log.w(TAG, "Unable to delete temp avatar file");
        }
    }
  }

  @Override
  public void onFailure() {}

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof IOException;
  }

  public static final class Factory implements Job.Factory<AvatarGroupsV2DownloadJob> {
    @Override
    public @NonNull AvatarGroupsV2DownloadJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      return new AvatarGroupsV2DownloadJob(parameters,
                                           GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2(),
                                           data.getString(CDN_KEY));
    }
  }
}
