/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.BatteryNotLowConstraint
import org.thoughtcrime.securesms.jobmanager.impl.DiskSpaceNotLowConstraint
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.jobmanager.impl.WifiConstraint
import org.thoughtcrime.securesms.jobs.CheckRestoreMediaLeftJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import org.thoughtcrime.securesms.testutil.SignalStoreRule
import org.thoughtcrime.securesms.testutil.SystemOutLogger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ArchiveRestoreProgressTest {

  @get:Rule
  val signalStore = SignalStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  private val attachments: AttachmentTable = mockk()

  private var remainingRestorableBytes = 0L
  private var wifiMet = true
  private var networkMet = true
  private var batteryMet = true
  private var diskMet = true

  @Before
  fun setUp() {
    val signalDatabase = mockk<SignalDatabase>(relaxed = true)
    mockkObject(SignalDatabase)
    every { SignalDatabase.instance } returns signalDatabase
    every { signalDatabase.attachmentTable } returns attachments
    every { attachments.getRemainingRestorableAttachmentSize() } answers { remainingRestorableBytes }

    mockkObject(WifiConstraint.Companion)
    every { WifiConstraint.isMet(any()) } answers { wifiMet }

    mockkStatic(NetworkConstraint::class)
    every { NetworkConstraint.isMet(any()) } answers { networkMet }

    mockkObject(BatteryNotLowConstraint.Companion)
    every { BatteryNotLowConstraint.isMet() } answers { batteryMet }

    mockkObject(DiskSpaceNotLowConstraint)
    every { DiskSpaceNotLowConstraint.isMet() } answers { diskMet }

    ArchiveRestoreProgress.resetForTesting()
  }

  @After
  fun tearDown() {
    unmockkObject(SignalDatabase, WifiConstraint.Companion, BatteryNotLowConstraint.Companion, DiskSpaceNotLowConstraint)
    unmockkStatic(NetworkConstraint::class)
  }

  @Test
  fun `startup - clears a stale PENDING state`() {
    SignalStore.backup.restoreState = RestoreState.PENDING

    ArchiveRestoreProgress.resetForTesting()

    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
  }

  @Test
  fun `startup - clears a stale RESTORING_DB state`() {
    SignalStore.backup.restoreState = RestoreState.RESTORING_DB

    ArchiveRestoreProgress.resetForTesting()

    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
  }

  @Test
  fun `startup - a stale RESTORING_DB state no longer reports an active restore`() {
    SignalStore.backup.restoreState = RestoreState.RESTORING_DB

    ArchiveRestoreProgress.resetForTesting()

    assertThat(ArchiveRestoreProgress.state.activelyRestoring()).isFalse()
  }

  @Test
  fun `startup - keeps media restore states`() {
    givenMediaToRestore(bytes = 100)

    for (state in listOf(RestoreState.CALCULATING_MEDIA, RestoreState.RESTORING_MEDIA, RestoreState.CANCELING_MEDIA)) {
      SignalStore.backup.restoreState = state

      ArchiveRestoreProgress.resetForTesting()

      assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(state)
      assertThat(SignalStore.backup.restoreState).isEqualTo(state)
    }
  }

  @Test
  fun `startup - keeps NONE`() {
    ArchiveRestoreProgress.resetForTesting()

    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(ArchiveRestoreProgress.state.activelyRestoring()).isFalse()
  }

  @Test
  fun `startup - seeds sizes from the persisted total`() {
    givenMediaToRestore(bytes = 100)
    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA

    ArchiveRestoreProgress.resetForTesting()

    val state = ArchiveRestoreProgress.state
    assertThat(state.totalRestoreSize).isEqualTo(100.bytes)
    assertThat(state.totalToRestoreThisRun).isEqualTo(100.bytes)
    assertThat(state.hasActivelyRestoredThisRun).isTrue()
  }

  @Test
  fun `onRestorePending - persists PENDING and reports an active restore`() {
    ArchiveRestoreProgress.onRestorePending()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.PENDING)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.PENDING)
    assertThat(ArchiveRestoreProgress.state.activelyRestoring()).isTrue()
  }

  @Test
  fun `onRestoringDb - persists RESTORING_DB`() {
    ArchiveRestoreProgress.onRestorePending()
    ArchiveRestoreProgress.onRestoringDb()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.RESTORING_DB)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.RESTORING_DB)
    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.RESTORING)
  }

  @Test
  fun `onRestoreFailed - resets to NONE`() {
    ArchiveRestoreProgress.onRestorePending()
    ArchiveRestoreProgress.onRestoringDb()

    ArchiveRestoreProgress.onRestoreFailed()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(ArchiveRestoreProgress.state.activelyRestoring()).isFalse()
  }

  @Test
  fun `onRestoreCanceled - resets PENDING to NONE`() {
    ArchiveRestoreProgress.onRestorePending()

    ArchiveRestoreProgress.onRestoreCanceled()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.NONE)
  }

  @Test
  fun `onRestoreCanceled - resets RESTORING_DB to NONE`() {
    ArchiveRestoreProgress.onRestorePending()
    ArchiveRestoreProgress.onRestoringDb()

    ArchiveRestoreProgress.onRestoreCanceled()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.NONE)
  }

  @Test
  fun `onRestoreCanceled - leaves a media restore that already started alone`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onStartMediaRestore()

    ArchiveRestoreProgress.onRestoreCanceled()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.CALCULATING_MEDIA)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.CALCULATING_MEDIA)
  }

  @Test
  fun `onStartMediaRestore - persists CALCULATING_MEDIA and the remaining size`() {
    remainingRestorableBytes = 100

    ArchiveRestoreProgress.onStartMediaRestore()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.CALCULATING_MEDIA)
    assertThat(SignalStore.backup.totalRestorableAttachmentSize).isEqualTo(100L)

    val state = ArchiveRestoreProgress.state
    assertThat(state.restoreState).isEqualTo(RestoreState.CALCULATING_MEDIA)
    assertThat(state.totalRestoreSize).isEqualTo(100.bytes)
    assertThat(state.remainingRestoreSize).isEqualTo(100.bytes)
    assertThat(state.hasActivelyRestoredThisRun).isTrue()
  }

  @Test
  fun `onRestoringMedia - persists RESTORING_MEDIA and reports progress`() {
    remainingRestorableBytes = 100
    ArchiveRestoreProgress.onStartMediaRestore()

    ArchiveRestoreProgress.onRestoringMedia()

    val state = ArchiveRestoreProgress.state
    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.RESTORING_MEDIA)
    assertThat(state.restoreState).isEqualTo(RestoreState.RESTORING_MEDIA)
    assertThat(state.restoreStatus).isEqualTo(RestoreStatus.RESTORING)
    assertThat(state.needRestoreMediaService()).isTrue()
  }

  @Test
  fun `onCancelMediaRestore - persists CANCELING_MEDIA`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()

    ArchiveRestoreProgress.onCancelMediaRestore()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.CANCELING_MEDIA)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.CANCELING_MEDIA)
  }

  @Test
  fun `allMediaRestored - finishes a media restore`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()
    remainingRestorableBytes = 0

    ArchiveRestoreProgress.allMediaRestored()

    val state = ArchiveRestoreProgress.state
    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(SignalStore.backup.totalRestorableAttachmentSize).isEqualTo(0L)
    assertThat(state.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(state.restoreStatus).isEqualTo(RestoreStatus.FINISHED)
  }

  @Test
  fun `allMediaRestored - finishing a cancel does not report FINISHED`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onCancelMediaRestore()
    remainingRestorableBytes = 0

    ArchiveRestoreProgress.allMediaRestored()

    val state = ArchiveRestoreProgress.state
    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(state.restoreStatus).isEqualTo(RestoreStatus.NONE)
    assertThat(state.hasActivelyRestoredThisRun).isFalse()
    assertThat(state.totalToRestoreThisRun).isEqualTo(0.bytes)
  }

  @Test
  fun `allMediaRestored - does nothing when no restore is running`() {
    SignalStore.backup.totalRestorableAttachmentSize = 100

    ArchiveRestoreProgress.allMediaRestored()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(SignalStore.backup.totalRestorableAttachmentSize).isEqualTo(100L)
  }

  @Test
  fun `clearFinishedStatus - resets a FINISHED status`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()
    remainingRestorableBytes = 0
    ArchiveRestoreProgress.allMediaRestored()

    ArchiveRestoreProgress.clearFinishedStatus()

    val state = ArchiveRestoreProgress.state
    assertThat(state.restoreStatus).isEqualTo(RestoreStatus.NONE)
    assertThat(state.hasActivelyRestoredThisRun).isFalse()
    assertThat(state.totalToRestoreThisRun).isEqualTo(0.bytes)
  }

  @Test
  fun `update - clears a media restore with nothing left to restore`() {
    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA

    ArchiveRestoreProgress.forceUpdate()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.NONE)
    assertThat(ArchiveRestoreProgress.state.restoreState).isEqualTo(RestoreState.NONE)
  }

  @Test
  fun `update - does not self-heal in-process restore states`() {
    ArchiveRestoreProgress.onRestorePending()
    ArchiveRestoreProgress.onRestoringDb()

    ArchiveRestoreProgress.forceUpdate()

    assertThat(SignalStore.backup.restoreState).isEqualTo(RestoreState.RESTORING_DB)
  }

  @Test
  fun `update - waits for wifi when cellular restore is disabled`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()
    wifiMet = false

    ArchiveRestoreProgress.forceUpdate()

    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.WAITING_FOR_WIFI)
  }

  @Test
  fun `update - waits for internet when there is no network`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()
    networkMet = false

    ArchiveRestoreProgress.forceUpdate()

    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.WAITING_FOR_INTERNET)
  }

  @Test
  fun `update - reports low battery`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()
    batteryMet = false

    ArchiveRestoreProgress.forceUpdate()

    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.LOW_BATTERY)
  }

  @Test
  fun `update - reports low disk space`() {
    givenMediaToRestore(bytes = 100)
    ArchiveRestoreProgress.onRestoringMedia()
    diskMet = false

    ArchiveRestoreProgress.forceUpdate()

    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.NOT_ENOUGH_DISK_SPACE)
  }

  @Test
  fun `update - reports not enough disk space when the remaining media will not fit`() {
    givenMediaToRestore(bytes = 100)
    SignalStore.backup.spaceAvailableOnDiskBytes = 50
    ArchiveRestoreProgress.onRestoringMedia()

    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.NOT_ENOUGH_DISK_SPACE)
  }

  @Test
  fun `update - reports a local restore directory error`() {
    SignalStore.backup.localRestoreDirectoryError = true

    ArchiveRestoreProgress.forceUpdate()

    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.LOCAL_RESTORE_DIRECTORY_UNAVAILABLE)
  }

  @Test
  fun `clearLocalRestoreDirectoryError - clears the error status`() {
    SignalStore.backup.localRestoreDirectoryError = true
    ArchiveRestoreProgress.forceUpdate()

    ArchiveRestoreProgress.clearLocalRestoreDirectoryError()

    assertThat(SignalStore.backup.localRestoreDirectoryError).isFalse()
    assertThat(ArchiveRestoreProgress.state.restoreStatus).isEqualTo(RestoreStatus.NONE)
  }

  @Test
  fun `checkForStalledRestore - enqueues a recovery check when media remains but no jobs are running`() {
    givenMediaToRestore(bytes = 100)
    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA
    every { AppDependencies.jobManager.areFactoriesEmpty(any()) } returns true

    ArchiveRestoreProgress.checkForStalledRestore()

    verify(timeout = 5_000) { AppDependencies.jobManager.add(any<CheckRestoreMediaLeftJob>()) }
  }

  @Test
  fun `checkForStalledRestore - does nothing while restore jobs are still running`() {
    givenMediaToRestore(bytes = 100)
    SignalStore.backup.restoreState = RestoreState.RESTORING_MEDIA
    every { AppDependencies.jobManager.areFactoriesEmpty(any()) } returns false

    ArchiveRestoreProgress.checkForStalledRestore()

    verify(timeout = 5_000) { AppDependencies.jobManager.areFactoriesEmpty(any()) }
    verify(exactly = 0) { AppDependencies.jobManager.add(any<CheckRestoreMediaLeftJob>()) }
  }

  private fun givenMediaToRestore(bytes: Long) {
    remainingRestorableBytes = bytes
    SignalStore.backup.totalRestorableAttachmentSize = bytes
  }
}
