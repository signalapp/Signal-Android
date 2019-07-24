/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import android.view.View;
import android.widget.ProgressBar;

import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColorsLegacy;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase.Reader;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.AttachmentDownloadJob;
import org.thoughtcrime.securesms.jobs.CreateSignedPreKeyJob;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.FileUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class DatabaseUpgradeActivity extends BaseActivity {
  private static final String TAG = DatabaseUpgradeActivity.class.getSimpleName();

  public static final int NO_MORE_KEY_EXCHANGE_PREFIX_VERSION  = 46;
  public static final int MMS_BODY_VERSION                     = 46;
  public static final int TOFU_IDENTITIES_VERSION              = 50;
  public static final int CURVE25519_VERSION                   = 63;
  public static final int ASYMMETRIC_MASTER_SECRET_FIX_VERSION = 73;
  public static final int NO_V1_VERSION                        = 83;
  public static final int SIGNED_PREKEY_VERSION                = 83;
  public static final int NO_DECRYPT_QUEUE_VERSION             = 113;
  public static final int PUSH_DECRYPT_SERIAL_ID_VERSION       = 131;
  public static final int MIGRATE_SESSION_PLAINTEXT            = 136;
  public static final int CONTACTS_ACCOUNT_VERSION             = 136;
  public static final int MEDIA_DOWNLOAD_CONTROLS_VERSION      = 151;
  public static final int REDPHONE_SUPPORT_VERSION             = 157;
  public static final int NO_MORE_CANONICAL_DB_VERSION         = 276;
  public static final int PROFILES                             = 289;
  public static final int SCREENSHOTS                          = 300;
  public static final int PERSISTENT_BLOBS                     = 317;
  public static final int INTERNALIZE_CONTACTS                 = 317;
  public static final int SQLCIPHER                            = 334;
  public static final int SQLCIPHER_COMPLETE                   = 352;
  public static final int REMOVE_JOURNAL                       = 353;
  public static final int REMOVE_CACHE                         = 354;
  public static final int FULL_TEXT_SEARCH                     = 358;
  public static final int BAD_IMPORT_CLEANUP                   = 373;
  public static final int IMAGE_CACHE_CLEANUP                  = 406;
  public static final int WORKMANAGER_MIGRATION                = 408;
  public static final int COLOR_MIGRATION                      = 412;
  public static final int UNIDENTIFIED_DELIVERY                = 422;
  public static final int SIGNALING_KEY_DEPRECATION            = 447;
  public static final int CONVERSATION_SEARCH                  = 455;

  private static final SortedSet<Integer> UPGRADE_VERSIONS = new TreeSet<Integer>() {{
    add(NO_MORE_KEY_EXCHANGE_PREFIX_VERSION);
    add(TOFU_IDENTITIES_VERSION);
    add(CURVE25519_VERSION);
    add(ASYMMETRIC_MASTER_SECRET_FIX_VERSION);
    add(NO_V1_VERSION);
    add(SIGNED_PREKEY_VERSION);
    add(NO_DECRYPT_QUEUE_VERSION);
    add(PUSH_DECRYPT_SERIAL_ID_VERSION);
    add(MIGRATE_SESSION_PLAINTEXT);
    add(MEDIA_DOWNLOAD_CONTROLS_VERSION);
    add(REDPHONE_SUPPORT_VERSION);
    add(NO_MORE_CANONICAL_DB_VERSION);
    add(SCREENSHOTS);
    add(INTERNALIZE_CONTACTS);
    add(PERSISTENT_BLOBS);
    add(SQLCIPHER);
    add(SQLCIPHER_COMPLETE);
    add(REMOVE_CACHE);
    add(FULL_TEXT_SEARCH);
    add(BAD_IMPORT_CLEANUP);
    add(IMAGE_CACHE_CLEANUP);
    add(WORKMANAGER_MIGRATION);
    add(COLOR_MIGRATION);
    add(UNIDENTIFIED_DELIVERY);
    add(SIGNALING_KEY_DEPRECATION);
    add(CONVERSATION_SEARCH);
  }};

  private MasterSecret masterSecret;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    this.masterSecret = KeyCachingService.getMasterSecret(this);

    if (needsUpgradeTask()) {
      Log.i("DatabaseUpgradeActivity", "Upgrading...");
      setContentView(R.layout.database_upgrade_activity);

      ProgressBar indeterminateProgress = findViewById(R.id.indeterminate_progress);
      ProgressBar determinateProgress   = findViewById(R.id.determinate_progress);

      new DatabaseUpgradeTask(indeterminateProgress, determinateProgress)
          .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, VersionTracker.getLastSeenVersion(this));
    } else {
      VersionTracker.updateLastSeenVersion(this);
      updateNotifications(this);
      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }
  }

  private boolean needsUpgradeTask() {
    int currentVersionCode = Util.getCanonicalVersionCode();
    int lastSeenVersion    = VersionTracker.getLastSeenVersion(this);

    Log.i("DatabaseUpgradeActivity", "LastSeenVersion: " + lastSeenVersion);

    if (lastSeenVersion >= currentVersionCode)
      return false;

    for (int version : UPGRADE_VERSIONS) {
      Log.i("DatabaseUpgradeActivity", "Comparing: " + version);
      if (lastSeenVersion < version)
        return true;
    }

    return false;
  }

  public static boolean isUpdate(Context context) {
    int currentVersionCode  = Util.getCanonicalVersionCode();
    int previousVersionCode = VersionTracker.getLastSeenVersion(context);

    return previousVersionCode < currentVersionCode;
  }

  @SuppressLint("StaticFieldLeak")
  private void updateNotifications(final Context context) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        MessageNotifier.updateNotification(context);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  public interface DatabaseUpgradeListener {
    public void setProgress(int progress, int total);
  }

  @SuppressLint("StaticFieldLeak")
  private class DatabaseUpgradeTask extends AsyncTask<Integer, Double, Void>
      implements DatabaseUpgradeListener
  {

    private final ProgressBar indeterminateProgress;
    private final ProgressBar determinateProgress;

    DatabaseUpgradeTask(ProgressBar indeterminateProgress, ProgressBar determinateProgress) {
      this.indeterminateProgress = indeterminateProgress;
      this.determinateProgress   = determinateProgress;
    }

    @Override
    protected Void doInBackground(Integer... params) {
      Context context = DatabaseUpgradeActivity.this.getApplicationContext();

      Log.i("DatabaseUpgradeActivity", "Running background upgrade..");
      DatabaseFactory.getInstance(DatabaseUpgradeActivity.this)
                     .onApplicationLevelUpgrade(context, masterSecret, params[0], this);

      if (params[0] < CURVE25519_VERSION) {
        IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
      }

      if (params[0] < NO_V1_VERSION) {
        File v1sessions = new File(context.getFilesDir(), "sessions");

        if (v1sessions.exists() && v1sessions.isDirectory()) {
          File[] contents = v1sessions.listFiles();

          if (contents != null) {
            for (File session : contents) {
              session.delete();
            }
          }

          v1sessions.delete();
        }
      }

      if (params[0] < SIGNED_PREKEY_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new CreateSignedPreKeyJob(context));
      }

      if (params[0] < NO_DECRYPT_QUEUE_VERSION) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < PUSH_DECRYPT_SERIAL_ID_VERSION) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < MIGRATE_SESSION_PLAINTEXT) {
//        new TextSecureSessionStore(context, masterSecret).migrateSessions();
//        new TextSecurePreKeyStore(context, masterSecret).migrateRecords();

        IdentityKeyUtil.migrateIdentityKeys(context, masterSecret);
        scheduleMessagesInPushDatabase(context);;
      }

      if (params[0] < CONTACTS_ACCOUNT_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(false));
      }

      if (params[0] < MEDIA_DOWNLOAD_CONTROLS_VERSION) {
        schedulePendingIncomingParts(context);
      }

      if (params[0] < REDPHONE_SUPPORT_VERSION) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new RefreshAttributesJob());
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(false));
      }

      if (params[0] < PROFILES) {
        ApplicationContext.getInstance(getApplicationContext())
                          .getJobManager()
                          .add(new DirectoryRefreshJob(false));
      }

      if (params[0] < SCREENSHOTS) {
        boolean screenSecurity = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, true);
        TextSecurePreferences.setScreenSecurityEnabled(getApplicationContext(), screenSecurity);
      }

      if (params[0] < PERSISTENT_BLOBS) {
        File externalDir = context.getExternalFilesDir(null);

        if (externalDir != null && externalDir.isDirectory() && externalDir.exists()) {
          for (File blob : externalDir.listFiles()) {
            if (blob.exists() && blob.isFile()) blob.delete();
          }
        }
      }

      if (params[0] < INTERNALIZE_CONTACTS) {
        if (TextSecurePreferences.isPushRegistered(getApplicationContext())) {
          TextSecurePreferences.setHasSuccessfullyRetrievedDirectory(getApplicationContext(), true);
        }
      }

      if (params[0] < SQLCIPHER) {
        scheduleMessagesInPushDatabase(context);
      }

      if (params[0] < SQLCIPHER_COMPLETE) {
        File file = context.getDatabasePath("messages.db");
        if (file != null && file.exists()) file.delete();
      }

      if (params[0] < REMOVE_JOURNAL) {
        File file = context.getDatabasePath("messages.db-journal");
        if (file != null && file.exists()) file.delete();
      }

      if (params[0] < REMOVE_CACHE) {
        try {
          FileUtils.deleteDirectoryContents(context.getCacheDir());
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      if (params[0] < IMAGE_CACHE_CLEANUP) {
        try {
          FileUtils.deleteDirectoryContents(context.getExternalCacheDir());
          GlideApp.get(context).clearDiskCache();
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      // This migration became unnecessary after switching away from WorkManager
//      if (params[0] < WORKMANAGER_MIGRATION) {
//        Log.i(TAG, "Beginning migration of existing jobs to WorkManager");
//
//        JobManager        jobManager = ApplicationContext.getInstance(getApplicationContext()).getJobManager();
//        PersistentStorage storage    = new PersistentStorage(getApplicationContext(), "TextSecureJobs", new JavaJobSerializer());
//
//        for (Job job : storage.getAllUnencrypted()) {
//          jobManager.add(job);
//          Log.i(TAG, "Migrated job with class '" + job.getClass().getSimpleName() + "' to run on new JobManager.");
//        }
//      }

      if (params[0] < COLOR_MIGRATION) {
        long startTime = System.currentTimeMillis();
        DatabaseFactory.getRecipientDatabase(context).updateSystemContactColors((name, color) -> {
          if (color != null) {
            try {
              return MaterialColor.fromSerialized(color);
            } catch (MaterialColor.UnknownColorException e) {
              Log.w(TAG, "Encountered an unknown color during legacy color migration.", e);
              return ContactColorsLegacy.generateFor(name);
            }
          }
          return ContactColorsLegacy.generateFor(name);
        });
        Log.i(TAG, "Color migration took " + (System.currentTimeMillis() - startTime) + " ms");
      }

      if (params[0] < UNIDENTIFIED_DELIVERY) {
        if (TextSecurePreferences.isMultiDevice(context)) {
          Log.i(TAG, "MultiDevice: Disabling UD (will be re-enabled if possible after pending refresh).");
          TextSecurePreferences.setIsUnidentifiedDeliveryEnabled(context, false);
        }

        Log.i(TAG, "Scheduling UD attributes refresh.");
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new RefreshAttributesJob());
      }

      if (params[0] < SIGNALING_KEY_DEPRECATION) {
        Log.i(TAG, "Scheduling a RefreshAttributesJob to remove the signaling key remotely.");
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new RefreshAttributesJob());
      }

      return null;
    }

    private void schedulePendingIncomingParts(Context context) {
      final AttachmentDatabase       attachmentDb       = DatabaseFactory.getAttachmentDatabase(context);
      final MmsDatabase              mmsDb              = DatabaseFactory.getMmsDatabase(context);
      final List<DatabaseAttachment> pendingAttachments = DatabaseFactory.getAttachmentDatabase(context).getPendingAttachments();

      Log.i(TAG, pendingAttachments.size() + " pending parts.");
      for (DatabaseAttachment attachment : pendingAttachments) {
        final Reader        reader = mmsDb.readerFor(mmsDb.getMessage(attachment.getMmsId()));
        final MessageRecord record = reader.getNext();

        if (attachment.hasData()) {
          Log.i(TAG, "corrected a pending media part " + attachment.getAttachmentId() + "that already had data.");
          attachmentDb.setTransferState(attachment.getMmsId(), attachment.getAttachmentId(), AttachmentDatabase.TRANSFER_PROGRESS_DONE);
        } else if (record != null && !record.isOutgoing() && record.isPush()) {
          Log.i(TAG, "queuing new attachment download job for incoming push part " + attachment.getAttachmentId() + ".");
          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new AttachmentDownloadJob(attachment.getMmsId(), attachment.getAttachmentId(), false));
        }
        reader.close();
      }
    }

    private void scheduleMessagesInPushDatabase(Context context) {
      PushDatabase pushDatabase = DatabaseFactory.getPushDatabase(context);
      Cursor       pushReader   = null;

      try {
        pushReader = pushDatabase.getPending();

        while (pushReader != null && pushReader.moveToNext()) {
          ApplicationContext.getInstance(getApplicationContext())
                            .getJobManager()
                            .add(new PushDecryptJob(getApplicationContext(),
                                                    pushReader.getLong(pushReader.getColumnIndexOrThrow(PushDatabase.ID))));
        }
      } finally {
        if (pushReader != null)
          pushReader.close();
      }
    }

    @Override
    protected void onProgressUpdate(Double... update) {
      indeterminateProgress.setVisibility(View.GONE);
      determinateProgress.setVisibility(View.VISIBLE);

      double scaler = update[0];
      determinateProgress.setProgress((int)Math.floor(determinateProgress.getMax() * scaler));
    }

    @Override
    protected void onPostExecute(Void result) {
      VersionTracker.updateLastSeenVersion(DatabaseUpgradeActivity.this);
      updateNotifications(DatabaseUpgradeActivity.this);

      startActivity((Intent)getIntent().getParcelableExtra("next_intent"));
      finish();
    }

    @Override
    public void setProgress(int progress, int total) {
      publishProgress(((double)progress / (double)total));
    }
  }

}
