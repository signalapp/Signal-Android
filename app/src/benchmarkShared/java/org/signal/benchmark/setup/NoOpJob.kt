package org.signal.benchmark.setup

import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.AccountConsistencyWorkerJob
import org.thoughtcrime.securesms.jobs.ArchiveBackupIdReservationJob
import org.thoughtcrime.securesms.jobs.AvatarGroupsV2DownloadJob
import org.thoughtcrime.securesms.jobs.CreateReleaseChannelJob
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob
import org.thoughtcrime.securesms.jobs.FontDownloaderJob
import org.thoughtcrime.securesms.jobs.GroupRingCleanupJob
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.PostRegistrationBackupRedemptionJob
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshSvrCredentialsJob
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob
import org.thoughtcrime.securesms.jobs.ResetSvrGuessCountJob
import org.thoughtcrime.securesms.jobs.RestoreOptimizedMediaJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.jobs.RetrieveRemoteAnnouncementsJob
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.jobs.StoryOnboardingDownloadJob

/**
 * A [Job] that does nothing and always succeeds. Test setups substitute this for jobs whose
 * real implementations would hit the network at startup (and so would either generate noise
 * against the [DeviceTransferBlockingInterceptor][org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor]
 * or fail against unstubbed mocks). Use [replaceFactories] to apply the swap.
 */
class NoOpJob(parameters: Parameters) : Job(parameters) {
  override fun serialize(): ByteArray? = null
  override fun getFactoryKey(): String = KEY
  override fun run(): Result = Result.success()
  override fun onFailure() = Unit

  class Factory : Job.Factory<NoOpJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): NoOpJob = NoOpJob(parameters)
  }

  companion object {
    const val KEY = "NoOpJob"

    private val STARTUP_NETWORK_JOB_KEYS: Set<String> = setOf(
      AccountConsistencyWorkerJob.KEY,
      ArchiveBackupIdReservationJob.KEY,
      AvatarGroupsV2DownloadJob.KEY,
      CreateReleaseChannelJob.KEY,
      DirectoryRefreshJob.KEY,
      DownloadLatestEmojiDataJob.KEY,
      EmojiSearchIndexDownloadJob.KEY,
      FontDownloaderJob.KEY,
      GroupRingCleanupJob.KEY,
      GroupV2UpdateSelfProfileKeyJob.KEY,
      LinkedDeviceInactiveCheckJob.KEY,
      MultiDeviceProfileKeyUpdateJob.KEY,
      PostRegistrationBackupRedemptionJob.KEY,
      PreKeysSyncJob.KEY,
      ProfileUploadJob.KEY,
      RefreshAttributesJob.KEY,
      RefreshSvrCredentialsJob.KEY,
      RequestGroupV2InfoJob.KEY,
      ResetSvrGuessCountJob.KEY,
      RestoreOptimizedMediaJob.KEY,
      RetrieveProfileAvatarJob.KEY,
      RetrieveProfileJob.KEY,
      RetrieveRemoteAnnouncementsJob.KEY,
      RotateCertificateJob.KEY,
      StickerPackDownloadJob.KEY,
      StorageSyncJob.KEY,
      StoryOnboardingDownloadJob.KEY
    )

    fun replaceFactories(factories: Map<String, Job.Factory<*>>): Map<String, Job.Factory<*>> =
      factories.mapValues { if (it.key in STARTUP_NETWORK_JOB_KEYS) Factory() else it.value }
  }
}
