package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.thoughtcrime.securesms.conversation.v2.data.AvatarDownloadStateCache;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.jobmanager.JsonJobData;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
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
  private static final String KEY_CDN_KEY  = "cdn_key";
  private static final String KEY_FORCE    = "force";

  private final GroupId.V2 groupId;
  private final String     cdnKey;
  private final boolean    force;

  public static void enqueueUnblurredAvatar(@NonNull GroupId.V2 groupId) {
    SignalExecutors.BOUNDED.execute(() -> {
      String cdnKey = SignalDatabase.groups().getGroup(groupId).get().requireV2GroupProperties().getAvatarKey();
      AppDependencies.getJobManager().add(new AvatarGroupsV2DownloadJob(groupId, cdnKey, true));
    });
  }

  public AvatarGroupsV2DownloadJob(@NonNull GroupId.V2 groupId, @NonNull String cdnKey) {
    this(groupId, cdnKey, false);
  }

  public AvatarGroupsV2DownloadJob(@NonNull GroupId.V2 groupId, @NonNull String cdnKey, boolean force) {
    this(new Parameters.Builder()
                       .addConstraint(NetworkConstraint.KEY)
                       .setQueue("AvatarGroupsV2DownloadJob::" + groupId)
                       .setMaxAttempts(10)
                       .build(),
         groupId,
         cdnKey,
         force);
  }

  private AvatarGroupsV2DownloadJob(@NonNull Parameters parameters, @NonNull GroupId.V2 groupId, @NonNull String cdnKey, boolean force) {
    super(parameters);
    this.groupId = groupId;
    this.cdnKey  = cdnKey;
    this.force   = force;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder()
                   .putString(KEY_GROUP_ID, groupId.toString())
                   .putString(KEY_CDN_KEY, cdnKey)
                   .putBoolean(KEY_FORCE, force)
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

      Recipient recipient = Recipient.resolved(record.get().getRecipientId());
      if (recipient.getShouldBlurAvatar() && !force) {
        Log.w(TAG, "Marking group as having an avatar but not downloading because avatar is blurred");
        database.onAvatarUpdated(groupId, true);
        return;
      }

      Log.i(TAG, "Downloading new avatar for group " + groupId);
      if (force) AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.IN_PROGRESS);
      byte[] decryptedAvatar = downloadGroupAvatarBytes(context, record.get().requireV2GroupProperties().getGroupMasterKey(), cdnKey);

      AvatarHelper.setAvatar(context, record.get().getRecipientId(), decryptedAvatar != null ? new ByteArrayInputStream(decryptedAvatar) : null);
      database.onAvatarUpdated(groupId, true);
      if (force) AvatarDownloadStateCache.set(recipient, AvatarDownloadStateCache.DownloadState.FINISHED);

    } catch (NonSuccessfulResponseCodeException e) {
      if (force) AvatarDownloadStateCache.set(Recipient.resolved(record.get().getRecipientId()), AvatarDownloadStateCache.DownloadState.FAILED);
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
  public void onFailure() {
    if (force) AvatarDownloadStateCache.set(Recipient.externalPossiblyMigratedGroup(groupId), AvatarDownloadStateCache.DownloadState.FAILED);
  }

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
                                           data.getString(KEY_CDN_KEY),
                                           data.getBooleanOrDefault(KEY_FORCE, false));
    }
  }
}
