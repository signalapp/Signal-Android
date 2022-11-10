package org.thoughtcrime.securesms.jobs;

import android.app.Application;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.jobmanager.Constraint;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.JobMigration;
import org.thoughtcrime.securesms.jobmanager.impl.AutoDownloadEmojiConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.CellServiceConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.ChargingConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.ChargingConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.DecryptionsDrainedConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkOrCellServiceConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NotInCallConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NotInCallConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.migrations.PushDecryptMessageJobEnvelopeMigration;
import org.thoughtcrime.securesms.jobmanager.migrations.PushProcessMessageQueueJobMigration;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdFollowUpJobMigration;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdFollowUpJobMigration2;
import org.thoughtcrime.securesms.jobmanager.migrations.RecipientIdJobMigration;
import org.thoughtcrime.securesms.jobmanager.migrations.RetrieveProfileJobMigration;
import org.thoughtcrime.securesms.jobmanager.migrations.SendReadReceiptsJobMigration;
import org.thoughtcrime.securesms.jobmanager.migrations.SenderKeyDistributionSendJobRecipientMigration;
import org.thoughtcrime.securesms.migrations.AccountRecordMigrationJob;
import org.thoughtcrime.securesms.migrations.ApplyUnknownFieldsToSelfMigrationJob;
import org.thoughtcrime.securesms.migrations.AttachmentCleanupMigrationJob;
import org.thoughtcrime.securesms.migrations.AttributesMigrationJob;
import org.thoughtcrime.securesms.migrations.AvatarIdRemovalMigrationJob;
import org.thoughtcrime.securesms.migrations.AvatarMigrationJob;
import org.thoughtcrime.securesms.migrations.BackupNotificationMigrationJob;
import org.thoughtcrime.securesms.migrations.BlobStorageLocationMigrationJob;
import org.thoughtcrime.securesms.migrations.CachedAttachmentsMigrationJob;
import org.thoughtcrime.securesms.migrations.DatabaseMigrationJob;
import org.thoughtcrime.securesms.migrations.DeleteDeprecatedLogsMigrationJob;
import org.thoughtcrime.securesms.migrations.DirectoryRefreshMigrationJob;
import org.thoughtcrime.securesms.migrations.EmojiDownloadMigrationJob;
import org.thoughtcrime.securesms.migrations.KbsEnclaveMigrationJob;
import org.thoughtcrime.securesms.migrations.LegacyMigrationJob;
import org.thoughtcrime.securesms.migrations.MigrationCompleteJob;
import org.thoughtcrime.securesms.migrations.PassingMigrationJob;
import org.thoughtcrime.securesms.migrations.PinOptOutMigration;
import org.thoughtcrime.securesms.migrations.PinReminderMigrationJob;
import org.thoughtcrime.securesms.migrations.PniAccountInitializationMigrationJob;
import org.thoughtcrime.securesms.migrations.PniMigrationJob;
import org.thoughtcrime.securesms.migrations.ProfileMigrationJob;
import org.thoughtcrime.securesms.migrations.ProfileSharingUpdateMigrationJob;
import org.thoughtcrime.securesms.migrations.RecipientSearchMigrationJob;
import org.thoughtcrime.securesms.migrations.RegistrationPinV2MigrationJob;
import org.thoughtcrime.securesms.migrations.StickerAdditionMigrationJob;
import org.thoughtcrime.securesms.migrations.StickerDayByDayMigrationJob;
import org.thoughtcrime.securesms.migrations.StickerLaunchMigrationJob;
import org.thoughtcrime.securesms.migrations.StickerMyDailyLifeMigrationJob;
import org.thoughtcrime.securesms.migrations.StorageCapabilityMigrationJob;
import org.thoughtcrime.securesms.migrations.StorageServiceMigrationJob;
import org.thoughtcrime.securesms.migrations.StorageServiceSystemNameMigrationJob;
import org.thoughtcrime.securesms.migrations.StoryReadStateMigrationJob;
import org.thoughtcrime.securesms.migrations.StoryViewedReceiptsStateMigrationJob;
import org.thoughtcrime.securesms.migrations.SyncDistributionListsMigrationJob;
import org.thoughtcrime.securesms.migrations.TrimByLengthSettingsMigrationJob;
import org.thoughtcrime.securesms.migrations.UserNotificationMigrationJob;
import org.thoughtcrime.securesms.migrations.UuidMigrationJob;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JobManagerFactories {

  public static Map<String, Job.Factory> getJobFactories(@NonNull Application application) {
    return new HashMap<String, Job.Factory>() {{
      put(AttachmentCopyJob.KEY,                     new AttachmentCopyJob.Factory());
      put(AttachmentDownloadJob.KEY,                 new AttachmentDownloadJob.Factory());
      put(AttachmentUploadJob.KEY,                   new AttachmentUploadJob.Factory());
      put(AttachmentMarkUploadedJob.KEY,             new AttachmentMarkUploadedJob.Factory());
      put(AttachmentCompressionJob.KEY,              new AttachmentCompressionJob.Factory());
      put(AutomaticSessionResetJob.KEY,              new AutomaticSessionResetJob.Factory());
      put(AvatarGroupsV1DownloadJob.KEY,             new AvatarGroupsV1DownloadJob.Factory());
      put(AvatarGroupsV2DownloadJob.KEY,             new AvatarGroupsV2DownloadJob.Factory());
      put(BoostReceiptRequestResponseJob.KEY,        new BoostReceiptRequestResponseJob.Factory());
      put(CheckServiceReachabilityJob.KEY,           new CheckServiceReachabilityJob.Factory());
      put(CleanPreKeysJob.KEY,                       new CleanPreKeysJob.Factory());
      put(ClearFallbackKbsEnclaveJob.KEY,            new ClearFallbackKbsEnclaveJob.Factory());
      put(ConversationShortcutUpdateJob.KEY,         new ConversationShortcutUpdateJob.Factory());
      put(CreateReleaseChannelJob.KEY,               new CreateReleaseChannelJob.Factory());
      put(DirectoryRefreshJob.KEY,                   new DirectoryRefreshJob.Factory());
      put(DonationReceiptRedemptionJob.KEY,          new DonationReceiptRedemptionJob.Factory());
      put(DownloadLatestEmojiDataJob.KEY,            new DownloadLatestEmojiDataJob.Factory());
      put(EmojiSearchIndexDownloadJob.KEY,           new EmojiSearchIndexDownloadJob.Factory());
      put(FcmRefreshJob.KEY,                         new FcmRefreshJob.Factory());
      put(FetchRemoteMegaphoneImageJob.KEY,          new FetchRemoteMegaphoneImageJob.Factory());
      put(FontDownloaderJob.KEY,                     new FontDownloaderJob.Factory());
      put(ForceUpdateGroupV2Job.KEY,                 new ForceUpdateGroupV2Job.Factory());
      put(ForceUpdateGroupV2WorkerJob.KEY,           new ForceUpdateGroupV2WorkerJob.Factory());
      put(GiftSendJob.KEY,                           new GiftSendJob.Factory());
      put(SendPaymentsActivatedJob.KEY,              new SendPaymentsActivatedJob.Factory());
      put(GroupV1MigrationJob.KEY,                   new GroupV1MigrationJob.Factory());
      put(GroupCallUpdateSendJob.KEY,                new GroupCallUpdateSendJob.Factory());
      put(GroupCallPeekJob.KEY,                      new GroupCallPeekJob.Factory());
      put(GroupCallPeekWorkerJob.KEY,                new GroupCallPeekWorkerJob.Factory());
      put(GroupV2UpdateSelfProfileKeyJob.KEY,        new GroupV2UpdateSelfProfileKeyJob.Factory());
      put(KbsEnclaveMigrationWorkerJob.KEY,          new KbsEnclaveMigrationWorkerJob.Factory());
      put(LeaveGroupV2Job.KEY,                       new LeaveGroupV2Job.Factory());
      put(LeaveGroupV2WorkerJob.KEY,                 new LeaveGroupV2WorkerJob.Factory());
      put(LocalBackupJob.KEY,                        new LocalBackupJob.Factory());
      put(LocalBackupJobApi29.KEY,                   new LocalBackupJobApi29.Factory());
      put(MarkerJob.KEY,                             new MarkerJob.Factory());
      put(MmsDownloadJob.KEY,                        new MmsDownloadJob.Factory());
      put(MmsReceiveJob.KEY,                         new MmsReceiveJob.Factory());
      put(MmsSendJob.KEY,                            new MmsSendJob.Factory());
      put(MultiDeviceBlockedUpdateJob.KEY,           new MultiDeviceBlockedUpdateJob.Factory());
      put(MultiDeviceConfigurationUpdateJob.KEY,     new MultiDeviceConfigurationUpdateJob.Factory());
      put(MultiDeviceContactSyncJob.KEY,             new MultiDeviceContactSyncJob.Factory());
      put(MultiDeviceContactUpdateJob.KEY,           new MultiDeviceContactUpdateJob.Factory());
      put(MultiDeviceGroupUpdateJob.KEY,             new MultiDeviceGroupUpdateJob.Factory());
      put(MultiDeviceKeysUpdateJob.KEY,              new MultiDeviceKeysUpdateJob.Factory());
      put(MultiDeviceMessageRequestResponseJob.KEY,  new MultiDeviceMessageRequestResponseJob.Factory());
      put(MultiDeviceOutgoingPaymentSyncJob.KEY,     new MultiDeviceOutgoingPaymentSyncJob.Factory());
      put(MultiDevicePniIdentityUpdateJob.KEY,       new MultiDevicePniIdentityUpdateJob.Factory());
      put(MultiDeviceProfileContentUpdateJob.KEY,    new MultiDeviceProfileContentUpdateJob.Factory());
      put(MultiDeviceProfileKeyUpdateJob.KEY,        new MultiDeviceProfileKeyUpdateJob.Factory());
      put(MultiDeviceReadUpdateJob.KEY,              new MultiDeviceReadUpdateJob.Factory());
      put(MultiDeviceStickerPackOperationJob.KEY,    new MultiDeviceStickerPackOperationJob.Factory());
      put(MultiDeviceStickerPackSyncJob.KEY,         new MultiDeviceStickerPackSyncJob.Factory());
      put(MultiDeviceStorageSyncRequestJob.KEY,      new MultiDeviceStorageSyncRequestJob.Factory());
      put(MultiDeviceSubscriptionSyncRequestJob.KEY, new MultiDeviceSubscriptionSyncRequestJob.Factory());
      put(MultiDeviceVerifiedUpdateJob.KEY,          new MultiDeviceVerifiedUpdateJob.Factory());
      put(MultiDeviceViewOnceOpenJob.KEY,            new MultiDeviceViewOnceOpenJob.Factory());
      put(MultiDeviceViewedUpdateJob.KEY,            new MultiDeviceViewedUpdateJob.Factory());
      put(NullMessageSendJob.KEY,                    new NullMessageSendJob.Factory());
      put(PaymentLedgerUpdateJob.KEY,                new PaymentLedgerUpdateJob.Factory());
      put(PaymentNotificationSendJob.KEY,            new PaymentNotificationSendJob.Factory());
      put(PaymentNotificationSendJobV2.KEY,          new PaymentNotificationSendJobV2.Factory());
      put(PaymentSendJob.KEY,                        new PaymentSendJob.Factory());
      put(PaymentTransactionCheckJob.KEY,            new PaymentTransactionCheckJob.Factory());
      put(PnpInitializeDevicesJob.KEY,               new PnpInitializeDevicesJob.Factory());
      put(PreKeysSyncJob.KEY,                        new PreKeysSyncJob.Factory());
      put(ProfileKeySendJob.KEY,                     new ProfileKeySendJob.Factory());
      put(ProfileUploadJob.KEY,                      new ProfileUploadJob.Factory());
      put(PushDecryptMessageJob.KEY,                 new PushDecryptMessageJob.Factory());
      put(PushDecryptDrainedJob.KEY,                 new PushDecryptDrainedJob.Factory());
      put(PushDistributionListSendJob.KEY,           new PushDistributionListSendJob.Factory());
      put(PushGroupSendJob.KEY,                      new PushGroupSendJob.Factory());
      put(PushGroupSilentUpdateSendJob.KEY,          new PushGroupSilentUpdateSendJob.Factory());
      put(PushMediaSendJob.KEY,                      new PushMediaSendJob.Factory());
      put(PushNotificationReceiveJob.KEY,            new PushNotificationReceiveJob.Factory());
      put(PushProcessEarlyMessagesJob.KEY,           new PushProcessEarlyMessagesJob.Factory());
      put(PushProcessMessageJob.KEY,                 new PushProcessMessageJob.Factory());
      put(PushTextSendJob.KEY,                       new PushTextSendJob.Factory());
      put(ReactionSendJob.KEY,                       new ReactionSendJob.Factory());
      put(RecipientChangedNumberJob.KEY,             new RecipientChangedNumberJob.Factory());
      put(RefreshAttributesJob.KEY,                  new RefreshAttributesJob.Factory());
      put(RefreshOwnProfileJob.KEY,                  new RefreshOwnProfileJob.Factory());
      put(RemoteConfigRefreshJob.KEY,                new RemoteConfigRefreshJob.Factory());
      put(RemoteDeleteSendJob.KEY,                   new RemoteDeleteSendJob.Factory());
      put(ReportSpamJob.KEY,                         new ReportSpamJob.Factory());
      put(ResendMessageJob.KEY,                      new ResendMessageJob.Factory());
      put(ResumableUploadSpecJob.KEY,                new ResumableUploadSpecJob.Factory());
      put(RequestGroupV2InfoWorkerJob.KEY,           new RequestGroupV2InfoWorkerJob.Factory());
      put(RequestGroupV2InfoJob.KEY,                 new RequestGroupV2InfoJob.Factory());
      put(RetrieveProfileAvatarJob.KEY,              new RetrieveProfileAvatarJob.Factory());
      put(RetrieveProfileJob.KEY,                    new RetrieveProfileJob.Factory());
      put(RetrieveRemoteAnnouncementsJob.KEY,        new RetrieveRemoteAnnouncementsJob.Factory());
      put(RotateCertificateJob.KEY,                  new RotateCertificateJob.Factory());
      put(RotateProfileKeyJob.KEY,                   new RotateProfileKeyJob.Factory());
      put(SenderKeyDistributionSendJob.KEY,          new SenderKeyDistributionSendJob.Factory());
      put(SendDeliveryReceiptJob.KEY,                new SendDeliveryReceiptJob.Factory());
      put(SendReadReceiptJob.KEY,                    new SendReadReceiptJob.Factory(application));
      put(SendRetryReceiptJob.KEY,                   new SendRetryReceiptJob.Factory());
      put(SendViewedReceiptJob.KEY,                  new SendViewedReceiptJob.Factory(application));
      put(SyncSystemContactLinksJob.KEY,             new SyncSystemContactLinksJob.Factory());
      put(MultiDeviceStorySendSyncJob.KEY,           new MultiDeviceStorySendSyncJob.Factory());
      put(ServiceOutageDetectionJob.KEY,             new ServiceOutageDetectionJob.Factory());
      put(SmsReceiveJob.KEY,                         new SmsReceiveJob.Factory());
      put(SmsSendJob.KEY,                            new SmsSendJob.Factory());
      put(SmsSentJob.KEY,                            new SmsSentJob.Factory());
      put(StickerDownloadJob.KEY,                    new StickerDownloadJob.Factory());
      put(StickerPackDownloadJob.KEY,                new StickerPackDownloadJob.Factory());
      put(StorageAccountRestoreJob.KEY,              new StorageAccountRestoreJob.Factory());
      put(StorageForcePushJob.KEY,                   new StorageForcePushJob.Factory());
      put(StorageSyncJob.KEY,                        new StorageSyncJob.Factory());
      put(SubscriptionKeepAliveJob.KEY,              new SubscriptionKeepAliveJob.Factory());
      put(SubscriptionReceiptRequestResponseJob.KEY, new SubscriptionReceiptRequestResponseJob.Factory());
      put(StoryOnboardingDownloadJob.KEY,            new StoryOnboardingDownloadJob.Factory());
      put(SubmitRateLimitPushChallengeJob.KEY,       new SubmitRateLimitPushChallengeJob.Factory());
      put(ThreadUpdateJob.KEY,                       new ThreadUpdateJob.Factory());
      put(TrimThreadJob.KEY,                         new TrimThreadJob.Factory());
      put(TypingSendJob.KEY,                         new TypingSendJob.Factory());
      put(UpdateApkJob.KEY,                          new UpdateApkJob.Factory());

      // Migrations
      put(AccountRecordMigrationJob.KEY,             new AccountRecordMigrationJob.Factory());
      put(ApplyUnknownFieldsToSelfMigrationJob.KEY,  new ApplyUnknownFieldsToSelfMigrationJob.Factory());
      put(AttachmentCleanupMigrationJob.KEY,         new AttachmentCleanupMigrationJob.Factory());
      put(AttributesMigrationJob.KEY,                new AttributesMigrationJob.Factory());
      put(AvatarIdRemovalMigrationJob.KEY,           new AvatarIdRemovalMigrationJob.Factory());
      put(AvatarMigrationJob.KEY,                    new AvatarMigrationJob.Factory());
      put(BackupNotificationMigrationJob.KEY,        new BackupNotificationMigrationJob.Factory());
      put(BlobStorageLocationMigrationJob.KEY,       new BlobStorageLocationMigrationJob.Factory());
      put(CachedAttachmentsMigrationJob.KEY,         new CachedAttachmentsMigrationJob.Factory());
      put(DatabaseMigrationJob.KEY,                  new DatabaseMigrationJob.Factory());
      put(DeleteDeprecatedLogsMigrationJob.KEY,      new DeleteDeprecatedLogsMigrationJob.Factory());
      put(DirectoryRefreshMigrationJob.KEY,          new DirectoryRefreshMigrationJob.Factory());
      put(EmojiDownloadMigrationJob.KEY,             new EmojiDownloadMigrationJob.Factory());
      put(KbsEnclaveMigrationJob.KEY,                new KbsEnclaveMigrationJob.Factory());
      put(LegacyMigrationJob.KEY,                    new LegacyMigrationJob.Factory());
      put(MigrationCompleteJob.KEY,                  new MigrationCompleteJob.Factory());
      put(SyncDistributionListsMigrationJob.KEY,     new SyncDistributionListsMigrationJob.Factory());
      put(PinOptOutMigration.KEY,                    new PinOptOutMigration.Factory());
      put(PinReminderMigrationJob.KEY,               new PinReminderMigrationJob.Factory());
      put(PniAccountInitializationMigrationJob.KEY,  new PniAccountInitializationMigrationJob.Factory());
      put(PniMigrationJob.KEY,                       new PniMigrationJob.Factory());
      put(ProfileMigrationJob.KEY,                   new ProfileMigrationJob.Factory());
      put(ProfileSharingUpdateMigrationJob.KEY,      new ProfileSharingUpdateMigrationJob.Factory());
      put(RecipientSearchMigrationJob.KEY,           new RecipientSearchMigrationJob.Factory());
      put(RegistrationPinV2MigrationJob.KEY,         new RegistrationPinV2MigrationJob.Factory());
      put(StickerLaunchMigrationJob.KEY,             new StickerLaunchMigrationJob.Factory());
      put(StickerAdditionMigrationJob.KEY,           new StickerAdditionMigrationJob.Factory());
      put(StickerDayByDayMigrationJob.KEY,           new StickerDayByDayMigrationJob.Factory());
      put(StickerMyDailyLifeMigrationJob.KEY,        new StickerMyDailyLifeMigrationJob.Factory());
      put(StorageCapabilityMigrationJob.KEY,         new StorageCapabilityMigrationJob.Factory());
      put(StorageServiceMigrationJob.KEY,            new StorageServiceMigrationJob.Factory());
      put(StorageServiceSystemNameMigrationJob.KEY,  new StorageServiceSystemNameMigrationJob.Factory());
      put(StoryReadStateMigrationJob.KEY,            new StoryReadStateMigrationJob.Factory());
      put(StoryViewedReceiptsStateMigrationJob.KEY,  new StoryViewedReceiptsStateMigrationJob.Factory());
      put(TrimByLengthSettingsMigrationJob.KEY,      new TrimByLengthSettingsMigrationJob.Factory());
      put(UserNotificationMigrationJob.KEY,          new UserNotificationMigrationJob.Factory());
      put(UuidMigrationJob.KEY,                      new UuidMigrationJob.Factory());

      // Dead jobs
      put(FailingJob.KEY,                            new FailingJob.Factory());
      put(PassingMigrationJob.KEY,                   new PassingMigrationJob.Factory());
      put("PushContentReceiveJob",                   new FailingJob.Factory());
      put("AttachmentUploadJob",                     new FailingJob.Factory());
      put("MmsSendJob",                              new FailingJob.Factory());
      put("RefreshUnidentifiedDeliveryAbilityJob",   new FailingJob.Factory());
      put("Argon2TestJob",                           new FailingJob.Factory());
      put("Argon2TestMigrationJob",                  new PassingMigrationJob.Factory());
      put("StorageKeyRotationMigrationJob",          new PassingMigrationJob.Factory());
      put("StorageSyncJob",                          new StorageSyncJob.Factory());
      put("WakeGroupV2Job",                          new FailingJob.Factory());
      put("LeaveGroupJob",                           new FailingJob.Factory());
      put("PushGroupUpdateJob",                      new FailingJob.Factory());
      put("RequestGroupInfoJob",                     new FailingJob.Factory());
      put("RotateSignedPreKeyJob",                   new PreKeysSyncJob.Factory());
      put("CreateSignedPreKeyJob",                   new PreKeysSyncJob.Factory());
      put("RefreshPreKeysJob",                       new PreKeysSyncJob.Factory());
    }};
  }

  public static Map<String, Constraint.Factory> getConstraintFactories(@NonNull Application application) {
    return new HashMap<String, Constraint.Factory>() {{
      put(AutoDownloadEmojiConstraint.KEY,           new AutoDownloadEmojiConstraint.Factory(application));
      put(ChargingConstraint.KEY,                    new ChargingConstraint.Factory());
      put(NetworkConstraint.KEY,                     new NetworkConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.KEY,        new NetworkOrCellServiceConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.LEGACY_KEY, new NetworkOrCellServiceConstraint.Factory(application));
      put(SqlCipherMigrationConstraint.KEY,          new SqlCipherMigrationConstraint.Factory(application));
      put(DecryptionsDrainedConstraint.KEY,          new DecryptionsDrainedConstraint.Factory());
      put(NotInCallConstraint.KEY,                   new NotInCallConstraint.Factory());
    }};
  }

  public static List<ConstraintObserver> getConstraintObservers(@NonNull Application application) {
    return Arrays.asList(CellServiceConstraintObserver.getInstance(application),
                         new ChargingConstraintObserver(application),
                         new NetworkConstraintObserver(application),
                         new SqlCipherMigrationConstraintObserver(),
                         new DecryptionsDrainedConstraintObserver(),
                         new NotInCallConstraintObserver());
  }

  public static List<JobMigration> getJobMigrations(@NonNull Application application) {
    return Arrays.asList(new RecipientIdJobMigration(application),
                         new RecipientIdFollowUpJobMigration(),
                         new RecipientIdFollowUpJobMigration2(),
                         new SendReadReceiptsJobMigration(SignalDatabase.mmsSms()),
                         new PushProcessMessageQueueJobMigration(application),
                         new RetrieveProfileJobMigration(),
                         new PushDecryptMessageJobEnvelopeMigration(application),
                         new SenderKeyDistributionSendJobRecipientMigration());
  }
}
