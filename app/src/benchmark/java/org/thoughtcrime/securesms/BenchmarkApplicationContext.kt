/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import android.app.Application
import org.signal.libsignal.net.Network
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.ApplicationDependencyProvider
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobmanager.JobMigrator
import org.thoughtcrime.securesms.jobmanager.impl.FactoryJobPredicate
import org.thoughtcrime.securesms.jobs.AccountConsistencyWorkerJob
import org.thoughtcrime.securesms.jobs.ArchiveBackupIdReservationJob
import org.thoughtcrime.securesms.jobs.AttachmentCompressionJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.CreateReleaseChannelJob
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob
import org.thoughtcrime.securesms.jobs.DownloadLatestEmojiDataJob
import org.thoughtcrime.securesms.jobs.EmojiSearchIndexDownloadJob
import org.thoughtcrime.securesms.jobs.FastJobStorage
import org.thoughtcrime.securesms.jobs.FontDownloaderJob
import org.thoughtcrime.securesms.jobs.GroupCallUpdateSendJob
import org.thoughtcrime.securesms.jobs.GroupRingCleanupJob
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob
import org.thoughtcrime.securesms.jobs.IndividualSendJob
import org.thoughtcrime.securesms.jobs.JobManagerFactories
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.jobs.MarkerJob
import org.thoughtcrime.securesms.jobs.MultiDeviceProfileKeyUpdateJob
import org.thoughtcrime.securesms.jobs.PostRegistrationBackupRedemptionJob
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob
import org.thoughtcrime.securesms.jobs.ProfileUploadJob
import org.thoughtcrime.securesms.jobs.PushGroupSendJob
import org.thoughtcrime.securesms.jobs.PushProcessMessageJob
import org.thoughtcrime.securesms.jobs.ReactionSendJob
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RetrieveRemoteAnnouncementsJob
import org.thoughtcrime.securesms.jobs.RotateCertificateJob
import org.thoughtcrime.securesms.jobs.SendDeliveryReceiptJob
import org.thoughtcrime.securesms.jobs.StickerPackDownloadJob
import org.thoughtcrime.securesms.jobs.StorageSyncJob
import org.thoughtcrime.securesms.jobs.StoryOnboardingDownloadJob
import org.thoughtcrime.securesms.jobs.TypingSendJob
import org.thoughtcrime.securesms.net.DeviceTransferBlockingInterceptor
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.signalservice.api.util.UptimeSleepTimer
import org.whispersystems.signalservice.api.websocket.SignalWebSocket
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration
import org.whispersystems.signalservice.internal.websocket.BenchmarkWebSocketConnection
import java.util.function.Supplier
import kotlin.time.Duration.Companion.seconds

class BenchmarkApplicationContext : ApplicationContext() {

  override fun initializeAppDependencies() {
    AppDependencies.init(this, BenchmarkDependencyProvider(this, ApplicationDependencyProvider(this)))

    DeviceTransferBlockingInterceptor.getInstance().blockNetwork()
  }

  override fun onForeground() = Unit

  class BenchmarkDependencyProvider(val application: Application, private val default: ApplicationDependencyProvider) : AppDependencies.Provider by default {
    override fun provideAuthWebSocket(
      signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>,
      libSignalNetworkSupplier: Supplier<Network>
    ): SignalWebSocket.AuthenticatedWebSocket {
      return SignalWebSocket.AuthenticatedWebSocket(
        connectionFactory = { BenchmarkWebSocketConnection.createAuthInstance() },
        canConnect = { true },
        sleepTimer = UptimeSleepTimer(),
        disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
      )
    }

    override fun provideUnauthWebSocket(
      signalServiceConfigurationSupplier: Supplier<SignalServiceConfiguration>,
      libSignalNetworkSupplier: Supplier<Network>
    ): SignalWebSocket.UnauthenticatedWebSocket {
      return SignalWebSocket.UnauthenticatedWebSocket(
        connectionFactory = { BenchmarkWebSocketConnection.createUnauthInstance() },
        canConnect = { true },
        sleepTimer = UptimeSleepTimer(),
        disconnectTimeoutMs = 15.seconds.inWholeMilliseconds
      )
    }

    override fun provideJobManager(): JobManager {
      val config = JobManager.Configuration.Builder()
        .setJobFactories(filterJobFactories(JobManagerFactories.getJobFactories(application)))
        .setConstraintFactories(JobManagerFactories.getConstraintFactories(application))
        .setConstraintObservers(JobManagerFactories.getConstraintObservers(application))
        .setJobStorage(FastJobStorage(JobDatabase.getInstance(application)))
        .setJobMigrator(JobMigrator(TextSecurePreferences.getJobManagerVersion(application), JobManager.CURRENT_VERSION, JobManagerFactories.getJobMigrations(application)))
        .addReservedJobRunner(FactoryJobPredicate(PushProcessMessageJob.KEY, MarkerJob.KEY))
        .addReservedJobRunner(FactoryJobPredicate(AttachmentUploadJob.KEY, AttachmentCompressionJob.KEY))
        .addReservedJobRunner(
          FactoryJobPredicate(
            IndividualSendJob.KEY,
            PushGroupSendJob.KEY,
            ReactionSendJob.KEY,
            TypingSendJob.KEY,
            GroupCallUpdateSendJob.KEY,
            SendDeliveryReceiptJob.KEY
          )
        )
        .build()
      return JobManager(application, config)
    }

    private fun filterJobFactories(jobFactories: Map<String, Job.Factory<*>>): Map<String, Job.Factory<*>> {
      val blockedJobs = setOf(
        AccountConsistencyWorkerJob.KEY,
        ArchiveBackupIdReservationJob.KEY,
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
        RetrieveRemoteAnnouncementsJob.KEY,
        RotateCertificateJob.KEY,
        StickerPackDownloadJob.KEY,
        StorageSyncJob.KEY,
        StoryOnboardingDownloadJob.KEY
      )

      return jobFactories.mapValues {
        if (it.key in blockedJobs) {
          NoOpJob.Factory()
        } else {
          it.value
        }
      }
    }
  }

  private class NoOpJob(parameters: Parameters) : Job(parameters) {

    companion object {
      const val KEY = "NoOpJob"
    }

    override fun serialize(): ByteArray? = null
    override fun getFactoryKey(): String = KEY
    override fun run(): Result = Result.success()
    override fun onFailure() = Unit

    class Factory : Job.Factory<NoOpJob> {
      override fun create(parameters: Parameters, serializedData: ByteArray?): NoOpJob {
        return NoOpJob(parameters)
      }
    }
  }
}
