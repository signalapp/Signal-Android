package org.thoughtcrime.securesms.migrations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.stickers.BlessedPacks;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.VersionTracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages application-level migrations.
 *
 * Migrations can be slotted to occur based on changes in the canonical version code
 * (see {@link Util#getCanonicalVersionCode()}).
 *
 * Migrations are performed via {@link MigrationJob}s. These jobs are durable and are run before any
 * other job, allowing you to schedule safe migrations. Furthermore, you may specify that a
 * migration is UI-blocking, at which point we will show a spinner via
 * {@link ApplicationMigrationActivity} if the user opens the app while the migration is in
 * progress.
 */
public class ApplicationMigrations {

  private static final String TAG = Log.tag(ApplicationMigrations.class);

  private static final MutableLiveData<Boolean> UI_BLOCKING_MIGRATION_RUNNING = new MutableLiveData<>();

  private static final int LEGACY_CANONICAL_VERSION = 455;

  @VisibleForTesting
  static final class Version {
    static final int LEGACY                        = 1;
    static final int RECIPIENT_ID                  = 2;
    static final int RECIPIENT_SEARCH              = 3;
    static final int RECIPIENT_CLEANUP             = 4;
    static final int AVATAR_MIGRATION              = 5;
    static final int UUIDS                         = 6;
    static final int CACHED_ATTACHMENTS            = 7;
    static final int STICKERS_LAUNCH               = 8;
    //static final int TEST_ARGON2          = 9;
    static final int SWOON_STICKERS                = 10;
    static final int STORAGE_SERVICE               = 11;
    //static final int STORAGE_KEY_ROTATE   = 12;
    static final int REMOVE_AVATAR_ID              = 13;
    static final int STORAGE_CAPABILITY            = 14;
    static final int PIN_REMINDER                  = 15;
    static final int VERSIONED_PROFILE             = 16;
    static final int PIN_OPT_OUT                   = 17;
    static final int TRIM_SETTINGS                 = 18;
    static final int THUMBNAIL_CLEANUP             = 19;
    static final int GV2                           = 20;
    static final int GV2_2                         = 21;
    static final int CDS                           = 22;
    static final int BACKUP_NOTIFICATION           = 23;
    static final int GV1_MIGRATION                 = 24;
    static final int USER_NOTIFICATION             = 25;
    static final int DAY_BY_DAY_STICKERS           = 26;
    static final int BLOB_LOCATION                 = 27;
    static final int SYSTEM_NAME_SPLIT             = 28;
    // Versions 29, 30 accidentally skipped
    static final int MUTE_SYNC                     = 31;
    static final int PROFILE_SHARING_UPDATE        = 32;
    static final int SMS_STORAGE_SYNC              = 33;
    static final int APPLY_UNIVERSAL_EXPIRE        = 34;
    static final int SENDER_KEY                    = 35;
    static final int SENDER_KEY_2                  = 36;
    static final int DB_AUTOINCREMENT              = 37;
    static final int ATTACHMENT_CLEANUP            = 38;
    static final int LOG_CLEANUP                   = 39;
    static final int ATTACHMENT_CLEANUP_2          = 40;
    static final int ANNOUNCEMENT_GROUP_CAPABILITY = 41;
    static final int STICKER_MY_DAILY_LIFE         = 42;
    static final int SENDER_KEY_3                  = 43;
    static final int CHANGE_NUMBER_SYNC            = 44;
    static final int CHANGE_NUMBER_CAPABILITY      = 45;
    static final int CHANGE_NUMBER_CAPABILITY_2    = 46;
    static final int DEFAULT_REACTIONS_SYNC        = 47;
    static final int DB_REACTIONS_MIGRATION        = 48;
    //static final int CHANGE_NUMBER_CAPABILITY_3  = 49;
    static final int PNI                           = 50;
    static final int FIX_DEPRECATION               = 51; // Only used to trigger clearing the 'client deprecated' flag
    static final int JUMBOMOJI_DOWNLOAD            = 52;
    static final int FIX_EMOJI_QUALITY             = 53;
    static final int CHANGE_NUMBER_CAPABILITY_4    = 54;
    //static final int KBS_MIGRATION               = 55;
    static final int PNI_IDENTITY                  = 56;
    static final int PNI_IDENTITY_2                = 57;
    static final int PNI_IDENTITY_3                = 58;
    static final int STORY_DISTRIBUTION_LIST_SYNC  = 59;
    static final int EMOJI_VERSION_7               = 60;
    static final int MY_STORY_PRIVACY_MODE         = 61;
    static final int REFRESH_EXPIRING_CREDENTIAL   = 62;
    static final int EMOJI_SEARCH_INDEX_10         = 63;
    static final int REFRESH_PNI_REGISTRATION_ID   = 64;
    //static final int KBS_MIGRATION_2             = 65;
    static final int PNI_2                         = 66;
    static final int SYSTEM_NAME_SYNC              = 67;
    static final int STORY_VIEWED_STATE            = 68;
//    static final int STORY_READ_STATE              = 69;
    static final int THREAD_MESSAGE_SCHEMA_CHANGE  = 70;
    static final int SMS_MMS_MERGE                 = 71;
    static final int REBUILD_MESSAGE_FTS_INDEX     = 72;
    static final int UPDATE_SMS_JOBS               = 73;
    static final int OPTIMIZE_MESSAGE_FTS_INDEX    = 74;
    static final int REACTION_DATABASE_MIGRATION   = 75;
    static final int REBUILD_MESSAGE_FTS_INDEX_2   = 76;
    static final int GLIDE_CACHE_CLEAR             = 77;
    static final int SYSTEM_NAME_RESYNC            = 78;
    static final int RECOVERY_PASSWORD_SYNC        = 79;
//    static final int DECRYPTIONS_DRAINED           = 80;
    static final int REBUILD_MESSAGE_FTS_INDEX_3   = 81;
    static final int TO_FROM_RECIPIENTS            = 82;
    static final int REBUILD_MESSAGE_FTS_INDEX_4   = 83;
    static final int INDEX_DATABASE_MIGRATION      = 84;
    static final int ACCOUNT_CONSISTENCY_CHECK     = 85;
    static final int BACKUP_JITTER                 = 86;
    static final int PREKEY_SYNC                   = 87;
    static final int DEDUPE_DB_MIGRATION           = 88;
    static final int DEDUPE_DB_MIGRATION_2         = 89;
    static final int EMOJI_VERSION_8               = 90;
    static final int SVR2_MIRROR                   = 91;
    static final int ATTACHMENT_CLEANUP_3          = 92;
    static final int EMOJI_SEARCH_INDEX_CHECK      = 93;
    static final int IDENTITY_FIX                  = 94;
    static final int COPY_USERNAME_TO_SIGNAL_STORE = 95;
    static final int RECHECK_PAYMENTS              = 96;
    static final int THREAD_COUNT_DB_MIGRATION     = 97;
    static final int SYNC_KEYS_MIGRATION           = 98;
    static final int SELF_REGISTERTED_STATE        = 99;
    static final int SVR2_ENCLAVE_UPDATE           = 100;
    static final int STORAGE_LOCAL_UNKNOWNS_FIX    = 101;
    static final int PNP_LAUNCH                    = 102;
    static final int EMOJI_VERSION_10              = 103;
    static final int ATTACHMENT_HASH_BACKFILL      = 104;
    static final int SUBSCRIBER_ID                 = 105;
    static final int CONTACT_LINK_REBUILD          = 106;
    static final int DELETE_SYNC_CAPABILITY        = 107;
    static final int REBUILD_MESSAGE_FTS_INDEX_5   = 108;
    static final int EXPIRE_TIMER_CAPABILITY       = 109;
    static final int REBUILD_MESSAGE_FTS_INDEX_6   = 110;
    static final int EXPIRE_TIMER_CAPABILITY_2     = 111;
//    static final int BACKFILL_DIGESTS              = 112;
    static final int BACKFILL_DIGESTS_V2           = 113;
    static final int CALL_LINK_STORAGE_SYNC        = 114;
    static final int WALLPAPER_MIGRATION           = 115;
    static final int BACKFILL_DIGESTS_V3           = 116;
    static final int SVR2_ENCLAVE_UPDATE_2         = 117;
    static final int WALLPAPER_MIGRATION_CLEANUP   = 118;
    static final int AEP_INTRODUCTION              = 119;
    static final int GROUP_EXTRAS_DB_FIX           = 120;
    static final int EMOJI_SEARCH_INDEX_CHECK_2    = 121;
    static final int QUOTE_AUTHOR_FIX              = 122;
    static final int BAD_E164_FIX                  = 123;
    static final int GPB_TOKEN_MIGRATION           = 124;
    static final int GROUP_ADD_MIGRATION           = 125;
    static final int SSRE2_CAPABILITY              = 126;
//    static final int FIX_INACTIVE_GROUPS           = 127;
    static final int DUPLICATE_E164_FIX            = 128;
    static final int FTS_TRIGGER_FIX               = 129;
    static final int THREAD_TABLE_PINNED_MIGRATION = 130;
    static final int GROUP_DECLINE_INVITE_FIX      = 131;
    static final int AVATAR_COLOR_MIGRATION_JOB    = 132;
    static final int DUPLICATE_E164_FIX_2          = 133;
    static final int E164_FORMATTING               = 134;
  }

  public static final int CURRENT_VERSION = 134;

 /**
   * This *must* be called after the {@link JobManager} has been instantiated, but *before* the call
   * to {@link JobManager#beginJobLoop()}. Otherwise, other non-migration jobs may have started
   * executing before we add the migration jobs.
   */
  public static void onApplicationCreate(@NonNull Context context, @NonNull JobManager jobManager) {
    if (isLegacyUpdate(context)) {
      Log.i(TAG, "Detected the need for a legacy update. Last seen canonical version: " + VersionTracker.getLastSeenVersion(context));
      TextSecurePreferences.setAppMigrationVersion(context, 0);
    }

    if (!isUpdate(context)) {
      Log.d(TAG, "Not an update. Skipping.");
      VersionTracker.updateLastSeenVersion(context);
      return;
    } else {
      Log.d(TAG, "About to update. Clearing deprecation flag.", true);
      SignalStore.misc().setClientDeprecated(false);
    }

    final int lastSeenVersion = TextSecurePreferences.getAppMigrationVersion(context);
    Log.d(TAG, "currentVersion: " + CURRENT_VERSION + ",  lastSeenVersion: " + lastSeenVersion);

    LinkedHashMap<Integer, MigrationJob> migrationJobs = getMigrationJobs(context, lastSeenVersion);

    if (migrationJobs.size() > 0) {
      Log.i(TAG, "About to enqueue " + migrationJobs.size() + " migration(s).");

      boolean uiBlocking        = true;
      int     uiBlockingVersion = lastSeenVersion;

      for (Map.Entry<Integer, MigrationJob> entry : migrationJobs.entrySet()) {
        int          version = entry.getKey();
        MigrationJob job     = entry.getValue();

        uiBlocking &= job.isUiBlocking();
        if (uiBlocking) {
          uiBlockingVersion = version;
        }

        jobManager.add(job);
        jobManager.add(new MigrationCompleteJob(version));
      }

      if (uiBlockingVersion > lastSeenVersion) {
        Log.i(TAG, "Migration set is UI-blocking through version " + uiBlockingVersion + ".");
        UI_BLOCKING_MIGRATION_RUNNING.setValue(true);
      } else {
        Log.i(TAG, "Migration set is non-UI-blocking.");
        UI_BLOCKING_MIGRATION_RUNNING.setValue(false);
      }

      final long startTime = System.currentTimeMillis();
      final int  uiVersion = uiBlockingVersion;

      EventBus.getDefault().register(new Object() {
        @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
        public void onMigrationComplete(MigrationCompleteEvent event) {
          Log.i(TAG, "Received MigrationCompleteEvent for version " + event.getVersion() + ". (Current: " + CURRENT_VERSION + ")");

          if (event.getVersion() > CURRENT_VERSION) {
            throw new AssertionError("Received a higher version than the current version? App downgrades are not supported. (received: " + event.getVersion() + ", current: " + CURRENT_VERSION + ")");
          }

          Log.i(TAG, "Updating last migration version to " + event.getVersion());
          TextSecurePreferences.setAppMigrationVersion(context, event.getVersion());

          if (event.getVersion() == CURRENT_VERSION) {
            Log.i(TAG, "Migration complete. Took " + (System.currentTimeMillis() - startTime) + " ms.");
            EventBus.getDefault().unregister(this);

            VersionTracker.updateLastSeenVersion(context);
            UI_BLOCKING_MIGRATION_RUNNING.setValue(false);
          } else if (event.getVersion() >= uiVersion) {
            Log.i(TAG, "Version is >= the UI-blocking version. Posting 'false'.");
            UI_BLOCKING_MIGRATION_RUNNING.setValue(false);
          }
        }
      });
    } else {
      Log.d(TAG, "No migrations.");
      TextSecurePreferences.setAppMigrationVersion(context, CURRENT_VERSION);
      VersionTracker.updateLastSeenVersion(context);
      UI_BLOCKING_MIGRATION_RUNNING.setValue(false);
    }
  }

  /**
   * @return A {@link LiveData} object that will update with whether or not a UI blocking migration
   * is in progress.
   */
  public static LiveData<Boolean> getUiBlockingMigrationStatus() {
    return UI_BLOCKING_MIGRATION_RUNNING;
  }

  /**
   * @return True if a UI blocking migration is running.
   */
  public static boolean isUiBlockingMigrationRunning() {
    Boolean value = UI_BLOCKING_MIGRATION_RUNNING.getValue();
    return value != null && value;
  }

  /**
   * @return Whether or not we're in the middle of an update, as determined by the last seen and
   * current version.
   */
  public static boolean isUpdate(@NonNull Context context) {
    return isLegacyUpdate(context) || TextSecurePreferences.getAppMigrationVersion(context) < CURRENT_VERSION;
  }

  private static LinkedHashMap<Integer, MigrationJob> getMigrationJobs(@NonNull Context context, int lastSeenVersion) {
    LinkedHashMap<Integer, MigrationJob> jobs = new LinkedHashMap<>();

    if (lastSeenVersion < Version.LEGACY) {
      jobs.put(Version.LEGACY, new LegacyMigrationJob());
    }

    if (lastSeenVersion < Version.RECIPIENT_ID) {
      jobs.put(Version.RECIPIENT_ID, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.RECIPIENT_SEARCH) {
      jobs.put(Version.RECIPIENT_SEARCH, new RecipientSearchMigrationJob());
    }

    if (lastSeenVersion < Version.RECIPIENT_CLEANUP) {
      jobs.put(Version.RECIPIENT_CLEANUP, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.AVATAR_MIGRATION) {
      jobs.put(Version.AVATAR_MIGRATION, new AvatarMigrationJob());
    }

    if (lastSeenVersion < Version.UUIDS) {
      jobs.put(Version.UUIDS, new UuidMigrationJob());
    }

    if (lastSeenVersion < Version.CACHED_ATTACHMENTS) {
      jobs.put(Version.CACHED_ATTACHMENTS, new CachedAttachmentsMigrationJob());
    }

    if (lastSeenVersion < Version.STICKERS_LAUNCH) {
      jobs.put(Version.STICKERS_LAUNCH, new StickerLaunchMigrationJob());
    }

    // This migration only triggered a test we aren't interested in any more.
    // if (lastSeenVersion < Version.TEST_ARGON2) {
    // jobs.put(Version.TEST_ARGON2, new Argon2TestMigrationJob());
    // }

    if (lastSeenVersion < Version.SWOON_STICKERS) {
      jobs.put(Version.SWOON_STICKERS, new StickerAdditionMigrationJob(BlessedPacks.SWOON_HANDS, BlessedPacks.SWOON_FACES));
    }

    if (lastSeenVersion < Version.STORAGE_SERVICE) {
      jobs.put(Version.STORAGE_SERVICE, new StorageServiceMigrationJob());
    }

    // Superceded by StorageCapabilityMigrationJob
//    if (lastSeenVersion < Version.STORAGE_KEY_ROTATE) {
//      jobs.put(Version.STORAGE_KEY_ROTATE, new StorageKeyRotationMigrationJob());
//    }

    if (lastSeenVersion < Version.REMOVE_AVATAR_ID) {
      jobs.put(Version.REMOVE_AVATAR_ID, new AvatarIdRemovalMigrationJob());
    }

    if (lastSeenVersion < Version.STORAGE_CAPABILITY) {
      jobs.put(Version.STORAGE_CAPABILITY, new StorageCapabilityMigrationJob());
    }

    if (lastSeenVersion < Version.PIN_REMINDER) {
      jobs.put(Version.PIN_REMINDER, new PinReminderMigrationJob());
    }

    if (lastSeenVersion < Version.VERSIONED_PROFILE) {
      jobs.put(Version.VERSIONED_PROFILE, new ProfileMigrationJob());
    }

    if (lastSeenVersion < Version.PIN_OPT_OUT) {
      jobs.put(Version.PIN_OPT_OUT, new PinOptOutMigration());
    }

    if (lastSeenVersion < Version.TRIM_SETTINGS) {
      jobs.put(Version.TRIM_SETTINGS, new TrimByLengthSettingsMigrationJob());
    }

    if (lastSeenVersion < Version.THUMBNAIL_CLEANUP) {
      jobs.put(Version.THUMBNAIL_CLEANUP, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.GV2) {
      jobs.put(Version.GV2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.GV2_2) {
      jobs.put(Version.GV2_2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.CDS) {
      jobs.put(Version.CDS, new DirectoryRefreshMigrationJob());
    }

    if (lastSeenVersion < Version.BACKUP_NOTIFICATION) {
      jobs.put(Version.BACKUP_NOTIFICATION, new BackupNotificationMigrationJob());
    }

    if (lastSeenVersion < Version.GV1_MIGRATION) {
      jobs.put(Version.GV1_MIGRATION, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.USER_NOTIFICATION) {
      jobs.put(Version.USER_NOTIFICATION, new UserNotificationMigrationJob());
    }

    if (lastSeenVersion < Version.DAY_BY_DAY_STICKERS) {
      jobs.put(Version.DAY_BY_DAY_STICKERS, new StickerDayByDayMigrationJob());
    }

    if (lastSeenVersion < Version.BLOB_LOCATION) {
      jobs.put(Version.BLOB_LOCATION, new BlobStorageLocationMigrationJob());
    }

    if (lastSeenVersion < Version.SYSTEM_NAME_SPLIT) {
      jobs.put(Version.SYSTEM_NAME_SPLIT, new DirectoryRefreshMigrationJob());
    }

    if (lastSeenVersion < Version.MUTE_SYNC) {
      jobs.put(Version.MUTE_SYNC, new StorageServiceMigrationJob());
    }

    if (lastSeenVersion < Version.PROFILE_SHARING_UPDATE) {
      jobs.put(Version.PROFILE_SHARING_UPDATE, new ProfileSharingUpdateMigrationJob());
    }

    if (lastSeenVersion < Version.SMS_STORAGE_SYNC) {
      jobs.put(Version.SMS_STORAGE_SYNC, new AccountRecordMigrationJob());
    }

    if (lastSeenVersion < Version.APPLY_UNIVERSAL_EXPIRE) {
      jobs.put(Version.APPLY_UNIVERSAL_EXPIRE, new ApplyUnknownFieldsToSelfMigrationJob());
    }

    if (lastSeenVersion < Version.SENDER_KEY) {
      jobs.put(Version.SENDER_KEY, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.SENDER_KEY_2) {
      jobs.put(Version.SENDER_KEY_2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.DB_AUTOINCREMENT) {
      jobs.put(Version.DB_AUTOINCREMENT, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.ATTACHMENT_CLEANUP) {
      jobs.put(Version.ATTACHMENT_CLEANUP, new AttachmentCleanupMigrationJob());
    }

    if (lastSeenVersion < Version.LOG_CLEANUP) {
      jobs.put(Version.LOG_CLEANUP, new DeleteDeprecatedLogsMigrationJob());
    }

    if (lastSeenVersion < Version.ATTACHMENT_CLEANUP_2) {
      jobs.put(Version.ATTACHMENT_CLEANUP_2, new AttachmentCleanupMigrationJob());
    }

    if (lastSeenVersion < Version.ANNOUNCEMENT_GROUP_CAPABILITY) {
      jobs.put(Version.ANNOUNCEMENT_GROUP_CAPABILITY, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.STICKER_MY_DAILY_LIFE) {
      jobs.put(Version.STICKER_MY_DAILY_LIFE, new StickerMyDailyLifeMigrationJob());
    }

    if (lastSeenVersion < Version.SENDER_KEY_3) {
      jobs.put(Version.SENDER_KEY_3, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.CHANGE_NUMBER_SYNC) {
      jobs.put(Version.CHANGE_NUMBER_SYNC, new AccountRecordMigrationJob());
    }

    if (lastSeenVersion < Version.CHANGE_NUMBER_CAPABILITY) {
      jobs.put(Version.CHANGE_NUMBER_CAPABILITY, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.CHANGE_NUMBER_CAPABILITY_2) {
      jobs.put(Version.CHANGE_NUMBER_CAPABILITY_2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.DEFAULT_REACTIONS_SYNC) {
      jobs.put(Version.DEFAULT_REACTIONS_SYNC, new StorageServiceMigrationJob());
    }

    if (lastSeenVersion < Version.DB_REACTIONS_MIGRATION) {
      jobs.put(Version.DB_REACTIONS_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.PNI) {
      jobs.put(Version.PNI, new PniMigrationJob());
    }

    if (lastSeenVersion < Version.JUMBOMOJI_DOWNLOAD) {
      jobs.put(Version.JUMBOMOJI_DOWNLOAD, new EmojiDownloadMigrationJob());
    }

    if (lastSeenVersion < Version.FIX_EMOJI_QUALITY) {
      jobs.put(Version.FIX_EMOJI_QUALITY, new EmojiDownloadMigrationJob());
    }

    if (lastSeenVersion < Version.CHANGE_NUMBER_CAPABILITY_4) {
      jobs.put(Version.CHANGE_NUMBER_CAPABILITY_4,new AttributesMigrationJob());
    }

    // if (lastSeenVersion < Version.KBS_MIGRATION) {
    //   jobs.put(Version.KBS_MIGRATION, new KbsEnclaveMigrationJob());
    // }

    if (lastSeenVersion < Version.PNI_IDENTITY) {
      jobs.put(Version.PNI_IDENTITY, new PniAccountInitializationMigrationJob());
    }

    if (lastSeenVersion < Version.PNI_IDENTITY_2) {
      jobs.put(Version.PNI_IDENTITY_2, new PniAccountInitializationMigrationJob());
    }

    if (lastSeenVersion < Version.PNI_IDENTITY_3) {
      jobs.put(Version.PNI_IDENTITY_3, new PniAccountInitializationMigrationJob());
    }

    if (lastSeenVersion < Version.STORY_DISTRIBUTION_LIST_SYNC) {
      jobs.put(Version.STORY_DISTRIBUTION_LIST_SYNC, new StorageServiceMigrationJob());
    }

    if (lastSeenVersion < Version.EMOJI_VERSION_7) {
      jobs.put(Version.EMOJI_VERSION_7, new EmojiDownloadMigrationJob());
    }

    if (lastSeenVersion < Version.MY_STORY_PRIVACY_MODE) {
      jobs.put(Version.MY_STORY_PRIVACY_MODE, new SyncDistributionListsMigrationJob());
    }

    if (lastSeenVersion < Version.REFRESH_EXPIRING_CREDENTIAL) {
      jobs.put(Version.REFRESH_EXPIRING_CREDENTIAL, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.EMOJI_SEARCH_INDEX_10) {
      jobs.put(Version.EMOJI_SEARCH_INDEX_10, new EmojiDownloadMigrationJob());
    }

    if (lastSeenVersion < Version.REFRESH_PNI_REGISTRATION_ID) {
      jobs.put(Version.REFRESH_PNI_REGISTRATION_ID, new AttributesMigrationJob());
    }

    // if (lastSeenVersion < Version.KBS_MIGRATION_2) {
    //   jobs.put(Version.KBS_MIGRATION_2, new KbsEnclaveMigrationJob());
    // }

    if (lastSeenVersion < Version.PNI_2) {
      jobs.put(Version.PNI_2, new PniMigrationJob());
    }

    if (lastSeenVersion < Version.SYSTEM_NAME_SYNC) {
      jobs.put(Version.SYSTEM_NAME_SYNC, new StorageServiceSystemNameMigrationJob());
    }

    if (lastSeenVersion < Version.STORY_VIEWED_STATE) {
      jobs.put(Version.STORY_VIEWED_STATE, new StoryViewedReceiptsStateMigrationJob());
    }

//    if (lastSeenVersion < Version.STORY_READ_STATE) {
//      jobs.put(Version.STORY_READ_STATE, new StoryReadStateMigrationJob());
//    }

    if (lastSeenVersion < Version.THREAD_MESSAGE_SCHEMA_CHANGE) {
      jobs.put(Version.THREAD_MESSAGE_SCHEMA_CHANGE, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.SMS_MMS_MERGE) {
      jobs.put(Version.SMS_MMS_MERGE, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.REBUILD_MESSAGE_FTS_INDEX) {
      jobs.put(Version.REBUILD_MESSAGE_FTS_INDEX, new RebuildMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.UPDATE_SMS_JOBS) {
      jobs.put(Version.UPDATE_SMS_JOBS, new UpdateSmsJobsMigrationJob());
    }

    if (lastSeenVersion < Version.OPTIMIZE_MESSAGE_FTS_INDEX) {
      jobs.put(Version.OPTIMIZE_MESSAGE_FTS_INDEX, new OptimizeMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.REACTION_DATABASE_MIGRATION) {
      jobs.put(Version.REACTION_DATABASE_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.REBUILD_MESSAGE_FTS_INDEX_2) {
      jobs.put(Version.REBUILD_MESSAGE_FTS_INDEX_2, new RebuildMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.GLIDE_CACHE_CLEAR) {
      jobs.put(Version.GLIDE_CACHE_CLEAR, new ClearGlideCacheMigrationJob());
    }

    if (lastSeenVersion < Version.SYSTEM_NAME_RESYNC) {
      jobs.put(Version.SYSTEM_NAME_RESYNC, new StorageServiceSystemNameMigrationJob());
    }

    if (lastSeenVersion < Version.RECOVERY_PASSWORD_SYNC) {
      jobs.put(Version.RECOVERY_PASSWORD_SYNC, new AttributesMigrationJob());
    }

    // Needed for the conversion to inline decryptions and is no longer necessary
    // if (lastSeenVersion < Version.DECRYPTIONS_DRAINED) {
    //   jobs.put(Version.DECRYPTIONS_DRAINED, new DecryptionsDrainedMigrationJob());
    // }

    if (lastSeenVersion < Version.REBUILD_MESSAGE_FTS_INDEX_3) {
      jobs.put(Version.REBUILD_MESSAGE_FTS_INDEX_3, new RebuildMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.TO_FROM_RECIPIENTS) {
      jobs.put(Version.TO_FROM_RECIPIENTS, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.REBUILD_MESSAGE_FTS_INDEX_4) {
      jobs.put(Version.REBUILD_MESSAGE_FTS_INDEX_4, new RebuildMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.INDEX_DATABASE_MIGRATION) {
      jobs.put(Version.INDEX_DATABASE_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.ACCOUNT_CONSISTENCY_CHECK) {
      jobs.put(Version.ACCOUNT_CONSISTENCY_CHECK, new AccountConsistencyMigrationJob());
    }

    if (lastSeenVersion < Version.BACKUP_JITTER) {
      jobs.put(Version.BACKUP_JITTER, new BackupJitterMigrationJob());
    }

    if (lastSeenVersion < Version.PREKEY_SYNC) {
      jobs.put(Version.PREKEY_SYNC, new PreKeysSyncMigrationJob());
    }

    if (lastSeenVersion < Version.DEDUPE_DB_MIGRATION) {
      jobs.put(Version.DEDUPE_DB_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.DEDUPE_DB_MIGRATION_2) {
      jobs.put(Version.DEDUPE_DB_MIGRATION_2, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.EMOJI_VERSION_8) {
      jobs.put(Version.EMOJI_VERSION_8, new EmojiDownloadMigrationJob());
    }

    if (lastSeenVersion < Version.SVR2_MIRROR) {
      jobs.put(Version.SVR2_MIRROR, new Svr2MirrorMigrationJob());
    }

    if (lastSeenVersion < Version.ATTACHMENT_CLEANUP_3) {
      jobs.put(Version.ATTACHMENT_CLEANUP_3, new AttachmentCleanupMigrationJob());
    }

    if (lastSeenVersion < Version.EMOJI_SEARCH_INDEX_CHECK) {
      jobs.put(Version.EMOJI_SEARCH_INDEX_CHECK, new EmojiSearchIndexCheckMigrationJob());
    }

    if (lastSeenVersion < Version.IDENTITY_FIX) {
      jobs.put(Version.IDENTITY_FIX, new IdentityTableCleanupMigrationJob());
    }

    if (lastSeenVersion < Version.COPY_USERNAME_TO_SIGNAL_STORE) {
      jobs.put(Version.COPY_USERNAME_TO_SIGNAL_STORE, new CopyUsernameToSignalStoreMigrationJob());
    }

    if (lastSeenVersion < Version.RECHECK_PAYMENTS) {
      jobs.put(Version.RECHECK_PAYMENTS, new RecheckPaymentsMigrationJob());
    }

    if (lastSeenVersion < Version.THREAD_COUNT_DB_MIGRATION) {
      jobs.put(Version.THREAD_COUNT_DB_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.SYNC_KEYS_MIGRATION) {
      jobs.put(Version.SYNC_KEYS_MIGRATION, new SyncKeysMigrationJob());
    }

    if (lastSeenVersion < Version.SELF_REGISTERTED_STATE) {
      jobs.put(Version.SELF_REGISTERTED_STATE,  new SelfRegisteredStateMigrationJob());
    }

    if (lastSeenVersion < Version.SVR2_ENCLAVE_UPDATE) {
      jobs.put(Version.SVR2_ENCLAVE_UPDATE,  new Svr2MirrorMigrationJob());
    }

    if (lastSeenVersion < Version.STORAGE_LOCAL_UNKNOWNS_FIX) {
      jobs.put(Version.STORAGE_LOCAL_UNKNOWNS_FIX, new StorageFixLocalUnknownMigrationJob());
    }

    if (lastSeenVersion < Version.PNP_LAUNCH) {
      jobs.put(Version.PNP_LAUNCH, new PnpLaunchMigrationJob());
    }

    if (lastSeenVersion < Version.EMOJI_VERSION_10) {
      jobs.put(Version.EMOJI_VERSION_10, new EmojiDownloadMigrationJob());
    }

    if (lastSeenVersion < Version.ATTACHMENT_HASH_BACKFILL) {
      jobs.put(Version.ATTACHMENT_HASH_BACKFILL, new AttachmentHashBackfillMigrationJob());
    }

    if (lastSeenVersion < Version.SUBSCRIBER_ID) {
      jobs.put(Version.SUBSCRIBER_ID, new SubscriberIdMigrationJob());
    }

    if (lastSeenVersion < Version.CONTACT_LINK_REBUILD) {
      jobs.put(Version.CONTACT_LINK_REBUILD, new ContactLinkRebuildMigrationJob());
    }

    if (lastSeenVersion < Version.DELETE_SYNC_CAPABILITY) {
      jobs.put(Version.DELETE_SYNC_CAPABILITY, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.REBUILD_MESSAGE_FTS_INDEX_5) {
      jobs.put(Version.REBUILD_MESSAGE_FTS_INDEX_5, new RebuildMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.EXPIRE_TIMER_CAPABILITY) {
      jobs.put(Version.EXPIRE_TIMER_CAPABILITY, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.REBUILD_MESSAGE_FTS_INDEX_6) {
      jobs.put(Version.REBUILD_MESSAGE_FTS_INDEX_6, new RebuildMessageSearchIndexMigrationJob());
    }

    if (lastSeenVersion < Version.EXPIRE_TIMER_CAPABILITY_2) {
      jobs.put(Version.EXPIRE_TIMER_CAPABILITY_2, new AttributesMigrationJob());
    }

    if (lastSeenVersion < Version.BACKFILL_DIGESTS_V2) {
      jobs.put(Version.BACKFILL_DIGESTS_V2, new BackfillDigestsMigrationJob());
    }

    if (lastSeenVersion < Version.CALL_LINK_STORAGE_SYNC) {
      jobs.put(Version.CALL_LINK_STORAGE_SYNC, new SyncCallLinksMigrationJob());
    }

    if (lastSeenVersion < Version.WALLPAPER_MIGRATION) {
      jobs.put(Version.WALLPAPER_MIGRATION, new WallpaperStorageMigrationJob());
    }

    if (lastSeenVersion < Version.BACKFILL_DIGESTS_V3) {
      jobs.put(Version.BACKFILL_DIGESTS_V3, new BackfillDigestsForDuplicatesMigrationJob());
    }

    if (lastSeenVersion < Version.SVR2_ENCLAVE_UPDATE_2) {
      jobs.put(Version.SVR2_ENCLAVE_UPDATE_2,  new Svr2MirrorMigrationJob());
    }

    if (lastSeenVersion < Version.WALLPAPER_MIGRATION_CLEANUP) {
      jobs.put(Version.WALLPAPER_MIGRATION_CLEANUP, new WallpaperCleanupMigrationJob());
    }

    if (lastSeenVersion < Version.AEP_INTRODUCTION) {
      jobs.put(Version.AEP_INTRODUCTION, new AepMigrationJob());
    }

    if (lastSeenVersion < Version.GROUP_EXTRAS_DB_FIX) {
      jobs.put(Version.GROUP_EXTRAS_DB_FIX, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.EMOJI_SEARCH_INDEX_CHECK_2) {
      jobs.put(Version.EMOJI_SEARCH_INDEX_CHECK_2, new EmojiSearchIndexCheckMigrationJob());
    }

    if (lastSeenVersion < Version.QUOTE_AUTHOR_FIX) {
      jobs.put(Version.QUOTE_AUTHOR_FIX, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.BAD_E164_FIX) {
      jobs.put(Version.BAD_E164_FIX, new BadE164MigrationJob());
    }

    if (lastSeenVersion < Version.GPB_TOKEN_MIGRATION) {
      jobs.put(Version.GPB_TOKEN_MIGRATION, new GooglePlayBillingPurchaseTokenMigrationJob());
    }

    if (lastSeenVersion < Version.GROUP_ADD_MIGRATION) {
      jobs.put(Version.GROUP_ADD_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.SSRE2_CAPABILITY) {
      jobs.put(Version.SSRE2_CAPABILITY, new AttributesMigrationJob());
    }

//    if (lastSeenVersion < Version.FIX_INACTIVE_GROUPS) {
//      jobs.put(Version.FIX_INACTIVE_GROUPS, new InactiveGroupCheckMigrationJob());
//    }

    if (lastSeenVersion < Version.DUPLICATE_E164_FIX) {
      jobs.put(Version.DUPLICATE_E164_FIX, new DuplicateE164MigrationJob());
    }

    if (lastSeenVersion < Version.FTS_TRIGGER_FIX) {
      jobs.put(Version.FTS_TRIGGER_FIX, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.THREAD_TABLE_PINNED_MIGRATION) {
      jobs.put(Version.THREAD_TABLE_PINNED_MIGRATION, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.GROUP_DECLINE_INVITE_FIX) {
      jobs.put(Version.GROUP_DECLINE_INVITE_FIX, new DatabaseMigrationJob());
    }

    if (lastSeenVersion < Version.AVATAR_COLOR_MIGRATION_JOB) {
      jobs.put(Version.AVATAR_COLOR_MIGRATION_JOB, new AvatarColorStorageServiceMigrationJob());
    }
    
    if (lastSeenVersion < Version.DUPLICATE_E164_FIX_2) {
      jobs.put(Version.DUPLICATE_E164_FIX_2, new DuplicateE164MigrationJob());
    }

    if (lastSeenVersion < Version.E164_FORMATTING) {
      jobs.put(Version.E164_FORMATTING, new E164FormattingMigrationJob());
    }

    return jobs;
  }

  private static boolean isLegacyUpdate(@NonNull Context context) {
    return VersionTracker.getLastSeenVersion(context) < LEGACY_CANONICAL_VERSION;
  }
}
