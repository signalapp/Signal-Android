package org.thoughtcrime.securesms.migrations;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Previously, we used a recipient's address as the filename for their avatar. We want to use
 * recipientId's instead in preparation for UUIDs.
 */
public class AvatarMigrationJob extends MigrationJob {

  public static final String KEY = "AvatarMigrationJob";

  private static final String TAG = Log.tag(AvatarMigrationJob.class);

  AvatarMigrationJob() {
    this(new Parameters.Builder().build());
  }

  private AvatarMigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public boolean isUiBlocking() {
    return true;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void performMigration() {
    File   oldDirectory = new File(context.getFilesDir(), "avatars");
    File[] files        = oldDirectory.listFiles();

    Log.i(TAG, "Preparing to move " + files.length + " avatars.");

    for (File file : files) {
      try {
        Recipient recipient = Recipient.external(context, file.getName());
        byte[]    data      = Util.readFully(new FileInputStream(file));

        AvatarHelper.setAvatar(context, recipient.getId(), data);
      } catch (IOException e) {
        Log.w(TAG, "Failed to copy avatar file. Skipping it.", e);
      } finally {
        file.delete();
      }
    }

    oldDirectory.delete();
  }

  @Override
  boolean shouldRetry(@NonNull Exception e) {
    return false;
  }

  public static class Factory implements Job.Factory<AvatarMigrationJob> {
    @Override
    public @NonNull AvatarMigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new AvatarMigrationJob(parameters);
    }
  }
}
