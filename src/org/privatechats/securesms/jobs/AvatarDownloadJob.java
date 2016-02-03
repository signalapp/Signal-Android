package org.privatechats.securesms.jobs;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.privatechats.securesms.BuildConfig;
import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.database.DatabaseFactory;
import org.privatechats.securesms.database.GroupDatabase;
import org.privatechats.securesms.jobs.requirements.MasterSecretRequirement;
import org.privatechats.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.privatechats.securesms.push.TextSecurePushTrustStore;
import org.privatechats.securesms.util.BitmapDecodingException;
import org.privatechats.securesms.util.BitmapUtil;
import org.privatechats.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.jobqueue.requirements.NetworkRequirement;
import org.whispersystems.textsecure.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.textsecure.internal.push.PushServiceSocket;
import org.whispersystems.textsecure.internal.util.StaticCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class AvatarDownloadJob extends MasterSecretJob {

  private static final String TAG = AvatarDownloadJob.class.getSimpleName();

  private final byte[] groupId;

  public AvatarDownloadJob(Context context, byte[] groupId) {
    super(context, JobParameters.newBuilder()
                                .withRequirement(new MasterSecretRequirement(context))
                                .withRequirement(new NetworkRequirement(context))
                                .withPersistence()
                                .create());

    this.groupId = groupId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun(MasterSecret masterSecret) throws IOException {
    GroupDatabase             database   = DatabaseFactory.getGroupDatabase(context);
    GroupDatabase.GroupRecord record     = database.getGroup(groupId);
    File                      attachment = null;

    try {
      if (record != null) {
        long   avatarId = record.getAvatarId();
        byte[] key      = record.getAvatarKey();
        String relay    = record.getRelay();

        if (avatarId == -1 || key == null) {
          return;
        }

        attachment = downloadAttachment(relay, avatarId);
        Bitmap avatar = BitmapUtil.createScaledBitmap(context, new AttachmentModel(attachment, key), 500, 500);

        database.updateAvatar(groupId, avatar);
      }
    } catch (BitmapDecodingException | NonSuccessfulResponseCodeException e) {
      Log.w(TAG, e);
    } finally {
      if (attachment != null)
        attachment.delete();
    }
  }

  @Override
  public void onCanceled() {}

  @Override
  public boolean onShouldRetryThrowable(Exception exception) {
    if (exception instanceof IOException) return true;
    return false;
  }

  private File downloadAttachment(String relay, long contentLocation) throws IOException {
    PushServiceSocket socket = new PushServiceSocket(BuildConfig.TEXTSECURE_URL,
                                                     new TextSecurePushTrustStore(context),
                                                     new StaticCredentialsProvider(TextSecurePreferences.getLocalNumber(context),
                                                                                   TextSecurePreferences.getPushServerPassword(context),
                                                                                   null),
                                                     BuildConfig.USER_AGENT);

    File destination = File.createTempFile("avatar", "tmp");

    destination.deleteOnExit();

    socket.retrieveAttachment(relay, contentLocation, destination, null);

    return destination;
  }

}
