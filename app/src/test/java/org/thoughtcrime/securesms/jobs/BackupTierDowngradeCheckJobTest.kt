/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import arrow.core.left
import arrow.core.right
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.core.util.logging.Log
import org.signal.libsignal.zkgroup.VerificationFailedException
import org.signal.network.service.ArchiveError
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger
import org.thoughtcrime.securesms.util.RemoteConfig
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

class BackupTierDowngradeCheckJobTest {

  companion object {
    private val FREE_TIER = MessageBackupTier.FREE.toBackupLevel()
  }

  @get:Rule
  val signalStore = MockSignalStoreRule()

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())

    every { signalStore.account.isRegistered } returns true
    every { signalStore.account.isPrimaryDevice } returns false
    every { signalStore.backup.backupTier } returns MessageBackupTier.PAID

    mockkObject(BackupRepository)
    mockkObject(RemoteConfig)
    every { RemoteConfig.defaultMaxBackoff } returns 1.minutes.inWholeMilliseconds
  }

  @After
  fun tearDown() {
    unmockkObject(BackupRepository)
    unmockkObject(RemoteConfig)
  }

  @Test
  fun `given the service says paid, when I run, then I keep the paid tier`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns MessageBackupTier.PAID.right()

    val result = BackupTierDowngradeCheckJob.create(FREE_TIER).run()

    assertTrue(result.isSuccess)
    verify { signalStore.backup.backupTier = MessageBackupTier.PAID }
  }

  @Test
  fun `given the service says free, when I run, then I downgrade to free`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns MessageBackupTier.FREE.right()

    val result = BackupTierDowngradeCheckJob.create(FREE_TIER).run()

    assertTrue(result.isSuccess)
    verify { signalStore.backup.backupTier = MessageBackupTier.FREE }
  }

  @Test
  fun `given a network error, when I run, then I retry and leave our tier alone`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns ArchiveError.NetworkError(IOException()).left()

    val result = BackupTierDowngradeCheckJob.create(FREE_TIER).run()

    assertTrue(result.isRetry)
    verify(exactly = 0) { signalStore.backup.backupTier = any() }
  }

  @Test
  fun `given the service rejects our credential, when I run, then I take the tier from the record`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns ArchiveError.CredentialError.Unauthorized().left()

    val result = BackupTierDowngradeCheckJob.create(FREE_TIER).run()

    assertTrue(result.isSuccess)
    verify { signalStore.backup.backupTier = MessageBackupTier.FREE }
  }

  @Test
  fun `given the service has no backup for us and the record had no tier, when I run, then I clear our tier`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns ArchiveError.CredentialError.NotFound().left()

    val result = BackupTierDowngradeCheckJob.create(null).run()

    assertTrue(result.isSuccess)
    verify { signalStore.backup.backupTier = null }
  }

  @Test
  fun `given we cannot verify our own credential, when I run, then I take the tier from the record`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns ArchiveError.CredentialError.ZkVerificationFailed(VerificationFailedException()).left()

    val result = BackupTierDowngradeCheckJob.create(null).run()

    assertTrue(result.isSuccess)
    verify { signalStore.backup.backupTier = null }
  }

  @Test
  fun `given a job carrying a tier, when I serialize and restore it, then I keep that tier`() {
    every { BackupRepository.getBackupTierWithoutDowngrade() } returns ArchiveError.CredentialError.NotFound().left()

    val job = BackupTierDowngradeCheckJob.create(FREE_TIER)
    val restored = BackupTierDowngradeCheckJob.Factory().create(job.parameters, job.serialize())

    val result = restored.run()

    assertTrue(result.isSuccess)
    verify { signalStore.backup.backupTier = MessageBackupTier.FREE }
  }

  @Test
  fun `given we are not registered, when I run, then I leave our tier alone`() {
    every { signalStore.account.isRegistered } returns false

    val result = BackupTierDowngradeCheckJob.create(FREE_TIER).run()

    assertTrue(result.isSuccess)
    verify(exactly = 0) { signalStore.backup.backupTier = any() }
  }

  @Test
  fun `given we are the primary, when I run, then I leave our tier alone`() {
    every { signalStore.account.isPrimaryDevice } returns true

    val result = BackupTierDowngradeCheckJob.create(FREE_TIER).run()

    assertTrue(result.isSuccess)
    verify(exactly = 0) { signalStore.backup.backupTier = any() }
  }
}
