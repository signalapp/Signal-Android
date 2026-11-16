/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.PlayStoreUtil
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LanguageSplitRepositoryTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val fixture = SplitInstallManagerFixture()
  private val manager = fixture.manager
  private val listener = fixture.listener
  private var managerCreated = false
  private val foregroundActions = mutableListOf<() -> Unit>()
  private val repository = createRepository(CoroutineScope(UnconfinedTestDispatcher()))

  @After
  fun tearDown() {
    AppLocale.reset()
  }

  @Before
  fun setUp() {
    installSplits(DENSITY_SPLIT)
  }

  @Test
  fun `a universal APK has every language`() {
    val languages = LanguageSplitRepository.readInstalledLanguages(ApplicationInfo(), languageSplits = true)

    assertThat(languages.isSplitInstall).isFalse()
    assertThat(languages.contains(Locale.GERMAN)).isTrue()
  }

  @Test
  fun `a bundle built without language splits is universal`() {
    val languages = LanguageSplitRepository.readInstalledLanguages(
      ApplicationInfo().apply {
        splitSourceDirs = arrayOf(
          "/data/app/pkg/split_config.arm64_v8a.apk",
          "/data/app/pkg/split_config.xxhdpi.apk"
        )
      },
      languageSplits = false
    )

    assertThat(languages.isSplitInstall).isFalse()
    assertThat(languages.contains(Locale.FRENCH)).isTrue()
  }

  @Test
  fun `a split install whose device language has no split has no languages`() {
    val languages = LanguageSplitRepository.readInstalledLanguages(
      ApplicationInfo().apply {
        splitSourceDirs = arrayOf(
          "/data/app/pkg/split_config.arm64_v8a.apk",
          "/data/app/pkg/split_config.xxhdpi.apk"
        )
      },
      languageSplits = true
    )

    assertThat(languages.isSplitInstall).isTrue()
    assertThat(languages.contains(Locale.FRENCH)).isFalse()
  }

  @Test
  fun `a split install only has the attached language splits`() {
    val languages = LanguageSplitRepository.readInstalledLanguages(
      ApplicationInfo().apply {
        splitSourceDirs = arrayOf(
          "/data/app/pkg/split_config.arm64_v8a.apk",
          "/data/app/pkg/split_config.xxhdpi.apk",
          "/data/app/pkg/split_config.en.apk",
          "/data/app/pkg/split_config.de.apk"
        )
      },
      languageSplits = true
    )

    assertThat(languages).isEqualTo(splitInstall("en", "de"))
  }

  @Test
  fun `a language split added after startup counts as installed`() {
    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.PlayStoreMissing)

    installSplits(DENSITY_SPLIT, "de")

    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.Installed)
  }

  @Test
  fun `a universal APK has every language and never talks to Play`() = runTest(UnconfinedTestDispatcher()) {
    installSplits()

    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.install("de").toList()).containsExactly(LanguageInstallStatus.Installed)
    repository.confirm(SESSION, mockk())
    assertThat(managerCreated).isFalse()
  }

  @Test
  fun `a split install reports missing languages as downloadable`() {
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = true)
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = true)

    assertThat(repository.availability("en")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.availability("zz")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.NeedsDownload)
  }

  @Test
  fun `a missing language cannot be downloaded with the Play Store disabled`() {
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = false)
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = true)

    assertThat(repository.availability("en")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.PlayStoreDisabled)
  }

  @Test
  fun `a missing language cannot be downloaded with Play services disabled`() {
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = true)
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = false)

    assertThat(repository.availability("en")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.PlayServicesDisabled)
  }

  @Test
  fun `a disabled Play Store wins over disabled Play services`() {
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = false)
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = false)

    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.PlayStoreDisabled)
  }

  @Test
  fun `a missing language cannot be downloaded without the Play Store`() {
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = true)

    assertThat(repository.availability("en")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.PlayStoreMissing)
  }

  @Test
  fun `a missing language cannot be downloaded without Play services`() {
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = true)

    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.PlayStoreMissing)
  }

  @Test
  fun `a language Play has installed counts as installed`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.installedLanguages } returns setOf("de")

    assertThat(repository.availability("de")).isEqualTo(LanguageAvailability.Installed)
    assertThat(repository.install("de").toList()).containsExactly(LanguageInstallStatus.Installed)
    verify(exactly = 0) { manager.startInstall(any()) }
  }

  @Test
  fun `the system default finishes as installed without talking to Play`() = runTest(UnconfinedTestDispatcher()) {
    assertThat(repository.install("zz").toList()).containsExactly(LanguageInstallStatus.Installed)
    verify(exactly = 0) { manager.startInstall(any()) }
  }

  @Test
  fun `English finishes as installed without talking to Play`() = runTest(UnconfinedTestDispatcher()) {
    assertThat(repository.install("en").toList()).containsExactly(LanguageInstallStatus.Installed)
    verify(exactly = 0) { manager.startInstall(any()) }
  }

  @Test
  fun `a language on disk finishes as installed without talking to Play`() = runTest(UnconfinedTestDispatcher()) {
    installSplits(DENSITY_SPLIT, "de")

    val states = collect(repository.install("de"))
    idleMainLooper()

    assertThat(states).containsExactly(LanguageInstallStatus.Installed)
    verify(exactly = 0) { manager.startInstall(any()) }
  }

  @Test
  fun `install reports the download and finishes when Play installs the split`() = runTest(UnconfinedTestDispatcher()) {
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.DOWNLOADING)
    fixture.update(SplitInstallSessionStatus.INSTALLED)

    assertThat(states).containsExactly(LanguageInstallStatus.Downloading, LanguageInstallStatus.Installed)
    verify { manager.unregisterListener(listener.captured) }
    verify(exactly = 0) { manager.cancelInstall(any()) }
  }

  @Test
  fun `events are held until the session id is known and other sessions are ignored`() = runTest(UnconfinedTestDispatcher()) {
    val start = TaskCompletionSource<Int>()
    every { manager.startInstall(any()) } returns start.task
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.DOWNLOADING)
    fixture.update(SplitInstallSessionStatus.INSTALLED, sessionId = SESSION + 1)
    assertThat(states).isEmpty()

    start.setResult(SESSION)

    assertThat(states).containsExactly(LanguageInstallStatus.Downloading)
  }

  @Test
  fun `a session id of zero means the language is already installed`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forResult(0)

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Installed)
    verify(exactly = 0) { manager.cancelInstall(any()) }
  }

  @Test
  fun `a refused request fails with Play's error`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.PLAY_STORE_NOT_FOUND))

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.PLAY_STORE_NOT_FOUND))
  }

  @Test
  fun `an unexpected exception is an internal error`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(IllegalStateException("boom"))

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.INTERNAL_ERROR))
  }

  @Test
  fun `a cancelled request is an internal error`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forCanceled()

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.INTERNAL_ERROR))
  }

  @Test
  fun `a request Play Core throws on is an internal error`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } throws IllegalStateException("boom")

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.INTERNAL_ERROR))
    verify { manager.unregisterListener(listener.captured) }
  }

  @Test
  fun `a listener Play Core refuses is an internal error`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.registerListener(any()) } throws IllegalStateException("boom")

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.INTERNAL_ERROR))
    verify(exactly = 0) { manager.startInstall(any()) }
  }

  @Test
  fun `a finished session leaves nothing running`() = runTest(UnconfinedTestDispatcher()) {
    val parent = SupervisorJob()
    val repository = createRepository(CoroutineScope(parent + UnconfinedTestDispatcher()))
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.INSTALLED)

    assertThat(states).containsExactly(LanguageInstallStatus.Installed)
    assertThat(parent.children.toList()).isEmpty()
  }

  @Test
  fun `a busy Play follows the active session for the same language`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED))
    every { manager.sessionStates } returns Tasks.forResult(
      listOf(
        sessionState(SplitInstallSessionStatus.DOWNLOADING, sessionId = OTHER_SESSION, languages = listOf("fr")),
        sessionState(SplitInstallSessionStatus.DOWNLOADING, sessionId = SESSION)
      )
    )

    val states = collect(repository.install("de"))
    fixture.update(SplitInstallSessionStatus.INSTALLED, sessionId = OTHER_SESSION)
    fixture.update(SplitInstallSessionStatus.INSTALLED)

    assertThat(states).containsExactly(LanguageInstallStatus.Downloading, LanguageInstallStatus.Installed)
  }

  @Test
  fun `a busy Play follows the listener once it has reported the session`() = runTest(UnconfinedTestDispatcher()) {
    val sessions = TaskCompletionSource<List<SplitInstallSessionState>>()
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED))
    every { manager.sessionStates } returns sessions.task
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
    sessions.setResult(listOf(sessionState(SplitInstallSessionStatus.PENDING)))

    assertThat(states).containsExactly(LanguageInstallStatus.NeedsPlayConfirmation(SESSION))
    repository.confirm(SESSION, mockk())
    verify { manager.startConfirmationDialogForResult(any(), any<ActivityResultLauncher<IntentSenderRequest>>()) }
  }

  @Test
  fun `a busy Play reports a confirmation once when the listener repeats the snapshot`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED))
    every { manager.sessionStates } returns Tasks.forResult(listOf(sessionState(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)))
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)

    assertThat(states).containsExactly(LanguageInstallStatus.NeedsPlayConfirmation(SESSION))
  }

  @Test
  fun `a busy Play with no active session for the language fails`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION))
    every { manager.sessionStates } returns Tasks.forResult(listOf(sessionState(SplitInstallSessionStatus.CANCELING)))

    val states = collect(repository.install("de"))

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION))
  }

  @Test
  fun `a failed session reports its error`() = runTest(UnconfinedTestDispatcher()) {
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.FAILED, errorCode = SplitInstallErrorCode.NETWORK_ERROR)

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.NETWORK_ERROR))
  }

  @Test
  fun `a session that needs confirmation stays open`() = runTest(UnconfinedTestDispatcher()) {
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
    fixture.update(SplitInstallSessionStatus.PENDING)
    fixture.update(SplitInstallSessionStatus.CANCELED)

    assertThat(states).containsExactly(LanguageInstallStatus.NeedsPlayConfirmation(SESSION), LanguageInstallStatus.Downloading, LanguageInstallStatus.Cancelled)
  }

  @Test
  fun `canceling and unknown statuses are not reported`() = runTest(UnconfinedTestDispatcher()) {
    val states = collect(repository.install("de"))

    fixture.update(SplitInstallSessionStatus.CANCELING)
    fixture.update(UNLISTED_STATUS)
    fixture.update(SplitInstallSessionStatus.CANCELED)

    assertThat(states).containsExactly(LanguageInstallStatus.Cancelled)
  }

  @Test
  fun `leaving before the download finishes cancels the session`() = runTest(UnconfinedTestDispatcher()) {
    val states = mutableListOf<LanguageInstallStatus>()
    val job = backgroundScope.launch { repository.install("de").toList(states) }
    fixture.update(SplitInstallSessionStatus.DOWNLOADING)

    job.cancel()

    verify { manager.cancelInstall(SESSION) }
    verify { manager.unregisterListener(listener.captured) }
  }

  @Test
  fun `leaving before Play reports the session cancels it once it does`() = runTest(UnconfinedTestDispatcher()) {
    val start = TaskCompletionSource<Int>()
    every { manager.startInstall(any()) } returns start.task
    val job = backgroundScope.launch { repository.install("de").toList(mutableListOf()) }

    job.cancel()
    verify(exactly = 0) { manager.cancelInstall(any()) }
    start.setResult(SESSION)
    idleMainLooper()

    verify { manager.cancelInstall(SESSION) }
  }

  @Test
  fun `confirm shows Play's dialog for the session that asked for it`() = runTest(UnconfinedTestDispatcher()) {
    collect(repository.install("de"))
    val state = sessionState(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
    listener.captured.onStateUpdate(state)
    val launcher = mockk<ActivityResultLauncher<IntentSenderRequest>>()

    repository.confirm(SESSION, launcher)

    verify { manager.startConfirmationDialogForResult(state, launcher) }
    verify(exactly = 0) { manager.getSessionState(any()) }
  }

  @Test
  fun `confirm does nothing once the session no longer needs confirmation`() = runTest(UnconfinedTestDispatcher()) {
    collect(repository.install("de"))
    fixture.update(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)
    fixture.update(SplitInstallSessionStatus.DOWNLOADING)

    repository.confirm(SESSION, mockk())

    verify(exactly = 0) { manager.startConfirmationDialogForResult(any(), any<ActivityResultLauncher<IntentSenderRequest>>()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary asks Play for the device's language under the system default`() {
    useDeviceLanguage("de-rDE")
    enablePlay()

    repository.installMissingLanguagesIfNecessary("zz")

    verify { manager.deferredLanguageInstall(listOf(Locale.GERMANY)) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary does not ask a Play Store that is turned off or missing for the device's language`() {
    useDeviceLanguage("de-rDE")

    repository.installMissingLanguagesIfNecessary("zz")
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = false)
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = true)
    repository.installMissingLanguagesIfNecessary("zz")

    assertThat(managerCreated).isFalse()
  }

  @Test
  fun `installMissingLanguagesIfNecessary does not ask Play for a device language already on disk`() {
    useDeviceLanguage("de-rDE")
    installSplits(DENSITY_SPLIT, "de")
    enablePlay()

    repository.installMissingLanguagesIfNecessary("zz")

    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary does not ask Play for a device language Play counts as installed`() {
    useDeviceLanguage("de-rDE")
    enablePlay()
    every { manager.installedLanguages } returns setOf("de")

    repository.installMissingLanguagesIfNecessary("zz")

    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary does not ask Play for a device language the app does not support`() {
    useDeviceLanguage("fr-rFR")
    enablePlay()

    repository.installMissingLanguagesIfNecessary("zz")

    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary does not ask Play for English`() {
    useDeviceLanguage("en-rUS")
    enablePlay()

    repository.installMissingLanguagesIfNecessary("zz")

    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary downloads the missing chosen language and keeps the other splits`() = runTest(UnconfinedTestDispatcher()) {
    installSplits(DENSITY_SPLIT, "fr")
    enablePlay()
    initializeAppLocale("en", "de", "fr")

    createRepository(backgroundScope).installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    verify { manager.startInstall(any()) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
    verify(exactly = 0) { manager.deferredLanguageUninstall(any()) }
  }

  @Test
  fun `picking the language of the startup install follows its session`() = runTest(UnconfinedTestDispatcher()) {
    playIsBusyWithStartupSession()
    val repository = createStartupInstallRepository()
    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    val states = collect(repository.install("de"))
    idleMainLooper()
    installSplits(DENSITY_SPLIT, "de")
    fixture.update(SplitInstallSessionStatus.INSTALLED)

    assertThat(states).containsExactly(LanguageInstallStatus.Downloading, LanguageInstallStatus.Installed)
    assertThat(repository.pendingRestartLanguage.value).isEqualTo("de")
    verify(exactly = 0) { manager.cancelInstall(any()) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `the startup install keeps its session when the user stops following it`() = runTest(UnconfinedTestDispatcher()) {
    playIsBusyWithStartupSession()
    val repository = createStartupInstallRepository()
    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    val job = backgroundScope.launch { repository.install("de").collect { } }
    job.cancel()
    verify(exactly = 0) { manager.cancelInstall(any()) }
    installSplits(DENSITY_SPLIT, "de")
    fixture.update(SplitInstallSessionStatus.INSTALLED)

    assertThat(repository.pendingRestartLanguage.value).isEqualTo("de")
  }

  @Test
  fun `picking another language cancels the startup install`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returnsMany listOf(Tasks.forResult(SESSION), Tasks.forResult(OTHER_SESSION))
    enablePlay()
    initializeAppLocale("en", "de", "fr")
    val repository = createRepository(backgroundScope)
    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    collect(repository.install("fr"))
    idleMainLooper()

    verify { manager.cancelInstall(SESSION) }
    verify(exactly = 0) { manager.cancelInstall(OTHER_SESSION) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `the startup install stays stopped when the download of another language fails`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returnsMany listOf(Tasks.forResult(SESSION), Tasks.forResult(OTHER_SESSION))
    val repository = createStartupInstallRepository()
    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    val states = collect(repository.install("fr"))
    idleMainLooper()
    fixture.update(SplitInstallSessionStatus.FAILED, sessionId = OTHER_SESSION, errorCode = SplitInstallErrorCode.NETWORK_ERROR)
    idleMainLooper()

    assertThat(states).containsExactly(LanguageInstallStatus.Failed(SplitInstallErrorCode.NETWORK_ERROR))
    verify(exactly = 2) { manager.startInstall(any()) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `the startup install keeps going until a picked language is collected`() = runTest(UnconfinedTestDispatcher()) {
    val repository = createStartupInstallRepository()
    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    repository.install("zz")
    repository.install("fr")

    verify(exactly = 0) { manager.cancelInstall(any()) }
  }

  @Test
  fun `picking the system default or English cancels the startup install`() = runTest(UnconfinedTestDispatcher()) {
    enablePlay()
    initializeAppLocale("en", "de", "fr")
    for (language in listOf("zz", "en")) {
      val repository = createRepository(backgroundScope)
      repository.installMissingLanguagesIfNecessary("de")
      idleMainLooper()

      assertThat(repository.install(language).toList()).containsExactly(LanguageInstallStatus.Installed)
    }

    verify(exactly = 2) { manager.cancelInstall(SESSION) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary never talks to Play on a universal APK`() {
    useDeviceLanguage("de-rDE")
    installSplits()
    enablePlay()

    repository.installMissingLanguagesIfNecessary("zz")

    assertThat(managerCreated).isFalse()
  }

  @Test
  fun `installMissingLanguagesIfNecessary asks for a restart once the missing chosen language is on disk`() = runTest(UnconfinedTestDispatcher()) {
    val repository = createStartupInstallRepository()

    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()
    fixture.update(SplitInstallSessionStatus.DOWNLOADING)
    assertThat(repository.pendingRestartLanguage.value).isNull()
    installSplits(DENSITY_SPLIT, "de")
    fixture.update(SplitInstallSessionStatus.INSTALLED)

    assertThat(repository.pendingRestartLanguage.value).isEqualTo("de")
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }

    repository.dismissPendingRestart()
    assertThat(repository.pendingRestartLanguage.value).isNull()
  }

  @Test
  fun `installMissingLanguagesIfNecessary does nothing when the process started with the chosen language`() = runTest(UnconfinedTestDispatcher()) {
    installSplits(DENSITY_SPLIT, "de")
    val repository = createStartupInstallRepository()

    repository.installMissingLanguagesIfNecessary("de")
    repository.installMissingLanguagesIfNecessary("en")
    idleMainLooper()

    verify(exactly = 0) { manager.startInstall(any()) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `installMissingLanguagesIfNecessary does not download a chosen language on a universal APK`() = runTest(UnconfinedTestDispatcher()) {
    installSplits()

    createStartupInstallRepository().installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    assertThat(managerCreated).isFalse()
  }

  @Test
  fun `the startup install falls back to a deferred install when Play refuses`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.NETWORK_ERROR))
    val repository = createStartupInstallRepository()

    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    verify { manager.deferredLanguageInstall(listOf(Locale.GERMAN)) }
    assertThat(repository.pendingRestartLanguage.value).isNull()
  }

  @Test
  fun `the startup install cancels and falls back to a deferred install when Play asks for confirmation`() = runTest(UnconfinedTestDispatcher()) {
    createStartupInstallRepository().installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    fixture.update(SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION)

    verify { manager.cancelInstall(SESSION) }
    verify { manager.deferredLanguageInstall(listOf(Locale.GERMAN)) }
  }

  @Test
  fun `the startup install falls back to a deferred install when the session is cancelled`() = runTest(UnconfinedTestDispatcher()) {
    createStartupInstallRepository().installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    fixture.update(SplitInstallSessionStatus.CANCELED)

    verify { manager.deferredLanguageInstall(listOf(Locale.GERMAN)) }
  }

  @Test
  fun `the startup install does not ask for a restart when Play reports an install that is not on disk`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forResult(0)
    val repository = createStartupInstallRepository()

    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    assertThat(repository.pendingRestartLanguage.value).isNull()
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `the startup install logs what Play Core throws instead of crashing`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.NETWORK_ERROR))
    every { manager.deferredLanguageInstall(any()) } throws IllegalStateException("boom")

    createStartupInstallRepository().installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    verify { manager.deferredLanguageInstall(listOf(Locale.GERMAN)) }
  }

  @Test
  fun `the startup install does not fall back when Play will never serve this install`() = runTest(UnconfinedTestDispatcher()) {
    every { manager.startInstall(any()) } returns Tasks.forException(SplitInstallException(SplitInstallErrorCode.APP_NOT_OWNED))

    createStartupInstallRepository().installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
  }

  @Test
  fun `the startup install waits for the foreground`() = runTest(UnconfinedTestDispatcher()) {
    val repository = createStartupInstallRepository(isForegrounded = false)

    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
    verify(exactly = 0) { manager.startInstall(any()) }

    foregroundActions.single().invoke()
    idleMainLooper()

    verify { manager.startInstall(any()) }
  }

  @Test
  fun `the startup install asks for a restart without downloading when the split arrived while in the background`() = runTest(UnconfinedTestDispatcher()) {
    val repository = createStartupInstallRepository(isForegrounded = false)

    repository.installMissingLanguagesIfNecessary("de")
    installSplits(DENSITY_SPLIT, "de")
    foregroundActions.single().invoke()
    idleMainLooper()

    assertThat(repository.pendingRestartLanguage.value).isEqualTo("de")
    verify(exactly = 0) { manager.startInstall(any()) }
  }

  @Test
  fun `the startup install does not ask a Play Store that is turned off or missing`() = runTest(UnconfinedTestDispatcher()) {
    val repository = createStartupInstallRepository(isForegrounded = false)

    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = false)
    repository.installMissingLanguagesIfNecessary("de")
    shadowOf(context.packageManager).removePackage(PlayStoreUtil.PLAY_STORE_PACKAGE)
    repository.installMissingLanguagesIfNecessary("de")
    idleMainLooper()

    verify(exactly = 0) { manager.startInstall(any()) }
    verify(exactly = 0) { manager.deferredLanguageInstall(any()) }
    assertThat(foregroundActions).isEmpty()
  }

  /** Play starts the startup install's session, then refuses a second request while that session is downloading. */
  private fun playIsBusyWithStartupSession() {
    every { manager.startInstall(any()) } returnsMany listOf(
      Tasks.forResult(SESSION),
      Tasks.forException(SplitInstallException(SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION))
    )
    every { manager.sessionStates } returns Tasks.forResult(listOf(sessionState(SplitInstallSessionStatus.DOWNLOADING)))
  }

  /** A repository whose process started with the splits installed right now, with Play able to deliver the rest. */
  private fun TestScope.createStartupInstallRepository(isForegrounded: Boolean = true): LanguageSplitRepository {
    enablePlay()
    initializeAppLocale("en", "de", "fr")
    return createRepository(backgroundScope, isForegrounded)
  }

  /** Sets the device's language, with English, German and Polish as the app's translations. */
  private fun useDeviceLanguage(qualifiers: String) {
    RuntimeEnvironment.setQualifiers(qualifiers)
    initializeAppLocale("en", "de", "pl")
  }

  private fun enablePlay() {
    installPackage(PlayStoreUtil.PLAY_STORE_PACKAGE, enabled = true)
    installPackage(PlayServicesUtil.PLAY_SERVICES_PACKAGE, enabled = true)
  }

  private fun splitInstall(vararg languages: String) = InstalledLanguages(languages.toSet())

  private fun createRepository(scope: CoroutineScope, isForegrounded: Boolean = true): LanguageSplitRepository {
    return LanguageSplitRepository(
      context = context,
      languageSplits = true,
      managerFactory = {
        managerCreated = true
        manager
      },
      whenForegrounded = { if (isForegrounded) it() else foregroundActions += it },
      scope = scope
    )
  }

  companion object {
    private const val UNLISTED_STATUS = 42
    private const val DENSITY_SPLIT = "xxhdpi"
  }
}
