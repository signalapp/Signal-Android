/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.Task
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.signal.core.util.AppForegroundObserver
import org.signal.core.util.PlayServicesUtil
import org.signal.core.util.PlayStoreUtil
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the in-app language picker to Play-delivered split language APKs that are downloaded on demand.
 */
class LanguageSplitRepository @JvmOverloads constructor(
  context: Context,
  private val languageSplits: Boolean,
  managerFactory: (Context) -> SplitInstallManager = SplitInstallManagerFactory::create,
  private val whenForegrounded: (() -> Unit) -> Unit = ::runWhenForegrounded,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + SignalDispatchers.Default)
) {

  companion object {
    private val TAG = Log.tag(LanguageSplitRepository::class.java)

    private const val SYSTEM_DEFAULT = "zz"
    private val LANGUAGE_SPLIT = Regex("[a-z]{2,3}")

    /**
     * [languageSplits] is the build's flag: a bundle built without language splits still ships ABI and density splits,
     * and a Play install whose device language has no split looks the same on disk.
     */
    internal fun readInstalledLanguages(applicationInfo: ApplicationInfo, languageSplits: Boolean): InstalledLanguages {
      val splitSourceDirs = applicationInfo.splitSourceDirs.orEmpty()
      if (!languageSplits || splitSourceDirs.isEmpty()) {
        return InstalledLanguages(splitLanguageCodes = null)
      }

      val languages = splitSourceDirs
        .asSequence()
        .map { File(it).name.removePrefix("split_").removeSuffix(".apk") }
        .filter { it.startsWith("config.") }
        .map { it.removePrefix("config.") }
        .filter { LANGUAGE_SPLIT.matches(it) }
        .toSet()

      return InstalledLanguages(languages)
    }

    /** Null for the system default, which is always installed. */
    private fun parseLocale(language: String): Locale? {
      if (language == SYSTEM_DEFAULT) {
        return null
      }
      return LanguageString.parseLocale(language) ?: throw IllegalArgumentException("Unable to parse locale: $language")
    }
  }

  private val applicationContext: Context = context.applicationContext

  private val manager: SplitInstallManager by lazy { managerFactory(applicationContext) }

  private val pendingConfirmations = ConcurrentHashMap<Int, SplitInstallSessionState>()

  private val startupInstall = AtomicReference<StartupInstall?>(null)

  private val _pendingRestartLanguage = MutableStateFlow<String?>(null)

  /** A chosen language whose split arrived after this process started, so it only shows after a restart. */
  val pendingRestartLanguage: StateFlow<String?> = _pendingRestartLanguage.asStateFlow()

  fun availability(language: String): LanguageAvailability {
    val locale = parseLocale(language)
    if (locale == null || isInstalled(locale)) {
      return LanguageAvailability.Installed
    }
    return downloadAvailability()
  }

  /**
   * Downloads and installs [language] if necessary. A language already on disk finishes as [LanguageInstallStatus.Installed] right away.
   */
  fun install(language: String): Flow<LanguageInstallStatus> = flow {
    val locale = parseLocale(language)
    if (locale == null || isInstalled(locale)) {
      stopStartupInstall(keep = null)
      emit(LanguageInstallStatus.Installed)
    } else {
      stopStartupInstall(keep = locale)
      emitAll(requestInstall(locale))
    }
  }

  /** Shows Play's own confirmation for a session that reported [LanguageInstallStatus.NeedsPlayConfirmation]. The session then continues or is canceled. */
  fun confirm(sessionId: Int, launcher: ActivityResultLauncher<IntentSenderRequest>) {
    val state = pendingConfirmations[sessionId]
    if (state == null) {
      Log.w(TAG, "No pending confirmation for session $sessionId")
      return
    }
    manager.startConfirmationDialogForResult(state, launcher)
  }

  /**
   * Fetches the language the app should be showing when its split is missing. Language splits are never released.
   */
  fun installMissingLanguagesIfNecessary(language: String) {
    installDeviceLanguageIfNecessary(language)
    installChosenLanguageIfNecessary(language)
  }

  fun dismissPendingRestart() {
    _pendingRestartLanguage.value = null
  }

  /** Under the system default, asks Play in the background for the device's language when the app supports it, and it is not on disk. */
  private fun installDeviceLanguageIfNecessary(language: String) {
    if (LanguageString.parseLocale(language) != null) {
      return
    }

    val locale = AppLocale.targetLocale
    if (!AppLocale.isSupported(locale) || hasPackageSplit(locale)) {
      return
    }

    val availability = downloadAvailability()
    if (availability != LanguageAvailability.NeedsDownload) {
      Log.w(TAG, "Device language ${locale.toLanguageTag()} is not installed, and Play is $availability. Not requesting it.")
      return
    }

    if (isInstalledByPlay(locale)) {
      return
    }

    Log.w(TAG, "Device language ${locale.toLanguageTag()} is not installed. Requesting it in the background.")
    deferredInstall(locale)
  }

  /** Fetches the chosen language when this process started without its split, and asks for a restart once it is on disk. */
  private fun installChosenLanguageIfNecessary(language: String) {
    val locale = LanguageString.parseLocale(language) ?: return
    if (AppLocale.installedLanguages.contains(locale)) {
      return
    }

    val availability = downloadAvailability()
    if (availability != LanguageAvailability.NeedsDownload) {
      Log.w(TAG, "Language ${locale.toLanguageTag()} is chosen but not installed, and Play is $availability. Not requesting it.")
      return
    }

    whenForegrounded { downloadChosenLanguage(language, locale) }
  }

  private fun downloadChosenLanguage(language: String, locale: Locale) {
    if (hasPackageSplit(locale)) {
      _pendingRestartLanguage.value = language
      return
    }

    Log.w(TAG, "Language ${locale.toLanguageTag()} is chosen but not installed. Downloading it.")
    val job = scope.launch {
      try {
        var outcome: LanguageInstallStatus? = null
        requestInstall(locale)
          .takeWhile { it !is LanguageInstallStatus.NeedsPlayConfirmation }
          .collect { outcome = it }
        ensureActive()

        when (val result = outcome) {
          LanguageInstallStatus.Installed if hasPackageSplit(locale) -> _pendingRestartLanguage.value = language
          LanguageInstallStatus.Installed -> Log.w(TAG, "Play reports ${locale.toLanguageTag()} as installed but its split is not on disk")
          is LanguageInstallStatus.Failed if result.isPermanent -> {
            Log.w(TAG, "Play will not serve ${locale.toLanguageTag()} to this install (errorCode=${result.playErrorCode})")
          }
          else -> deferredInstall(locale)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Download of the chosen language ${locale.toLanguageTag()} failed", e)
      }
    }
    startupInstall.getAndSet(StartupInstall(locale, job))?.job?.cancel()
  }

  /** Stops the startup install unless it is for [keep]'s language. */
  private fun stopStartupInstall(keep: Locale?) {
    val startup = startupInstall.get() ?: return
    if (keep != null && LanguageString.matches(startup.locale.language, keep)) {
      return
    }
    if (startupInstall.compareAndSet(startup, null)) {
      startup.job.cancel()
    }
  }

  /** Asks Play to install [locale]'s split at some later point, without a session to follow. */
  private fun deferredInstall(locale: Locale) {
    manager.deferredLanguageInstall(listOf(locale))
      .addOnFailureListener { e -> Log.w(TAG, "Deferred install of ${locale.toLanguageTag()} failed", e) }
  }

  /** Play Store and Play Services are required to deliver missing languages. */
  private fun downloadAvailability(): LanguageAvailability {
    val packageManager = applicationContext.packageManager
    val playStore = packageManager.isEnabled(PlayStoreUtil.PLAY_STORE_PACKAGE)
    val playServices = packageManager.isEnabled(PlayServicesUtil.PLAY_SERVICES_PACKAGE)
    return when {
      playStore == null -> LanguageAvailability.PlayStoreMissing
      !playStore -> LanguageAvailability.PlayStoreDisabled
      playServices == null -> LanguageAvailability.PlayStoreMissing
      !playServices -> LanguageAvailability.PlayServicesDisabled
      else -> LanguageAvailability.NeedsDownload
    }
  }

  /** Whether the package has [locale]'s split right now, which includes any Play added after this process started. */
  private fun hasPackageSplit(locale: Locale): Boolean {
    val applicationInfo = applicationContext.packageManager.getApplicationInfo(applicationContext.packageName, 0)
    return readInstalledLanguages(applicationInfo, languageSplits).contains(locale)
  }

  /**
   * Whether the package has [locale]'s split or Play counts it as installed, which includes splits SplitCompat may
   * load from the app's storage in Android < 8.
   */
  private fun isInstalled(locale: Locale): Boolean {
    return hasPackageSplit(locale) || isInstalledByPlay(locale)
  }

  private fun isInstalledByPlay(locale: Locale): Boolean {
    return manager.installedLanguages.any { LanguageString.matches(it, locale) }
  }

  /**
   * Leaving before the session ends cancels Play's session if this request started it. Anything Play Core throws ends the
   * session as failed.
   *
   * When Play is busy with a session for the language, this follows it. Busy with anything else, this fails.
   */
  private fun requestInstall(locale: Locale): Flow<LanguageInstallStatus> = flow {
    val updates = Channel<SplitInstallSessionState>(Channel.UNLIMITED)
    val listener = SplitInstallStateUpdatedListener { updates.trySend(it) }
    var sessionId: Int? = null
    var started = false
    var finished = false

    /** Emits [state] if it belongs to the session and returns true once the session has ended. */
    suspend fun handle(state: SplitInstallSessionState): Boolean {
      if (state.sessionId() != sessionId) {
        return false
      }
      if (state.status() == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
        pendingConfirmations[state.sessionId()] = state
      } else {
        pendingConfirmations.remove(state.sessionId())
      }

      val status = state.toStatus() ?: return false
      if (status is LanguageInstallStatus.Failed) {
        Log.w(TAG, "Language split install of ${locale.toLanguageTag()} ended in status=${state.status()} errorCode=${state.errorCode()}")
      }
      finished = status.isFinal
      emit(status)
      return finished
    }

    var installTask: Task<Int>? = null
    try {
      manager.registerListener(listener)
      val task = manager.startInstall(SplitInstallRequest.newBuilder().addLanguage(locale).build()).also { installTask = it }
      val backlog = try {
        sessionId = task.await()
        started = true
        emptyList()
      } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        val errorCode = e.toErrorCode()
        Log.w(TAG, "Failed to start language split install for ${locale.toLanguageTag()}, errorCode=$errorCode", e)
        val active = if (isBusy(errorCode)) activeSession(locale) else null
        if (active == null) {
          finished = true
          emit(LanguageInstallStatus.Failed(errorCode))
          return@flow
        }
        Log.i(TAG, "Following the active session ${active.sessionId()} for ${locale.toLanguageTag()}")
        sessionId = active.sessionId()
        // Once the listener has reported the session, its updates are at least as new as the snapshot.
        val queued = generateSequence { updates.tryReceive().getOrNull() }.toList()
        if (queued.any { it.sessionId() == sessionId }) queued else listOf(active) + queued
      }

      if (sessionId == 0) {
        finished = true
        emit(LanguageInstallStatus.Installed)
        return@flow
      }

      for (state in backlog) {
        if (handle(state)) {
          return@flow
        }
      }
      for (state in updates) {
        if (handle(state)) {
          return@flow
        }
      }
    } finally {
      manager.unregisterListener(listener)
      sessionId?.let { pendingConfirmations.remove(it) }
      if (!finished) {
        if (sessionId == null) {
          installTask?.addOnSuccessListener { id ->
            if (id != 0) {
              manager.cancelInstall(id)
            }
          }
        } else if (started) {
          manager.cancelInstall(sessionId)
        }
      }
    }
  }.catch { e ->
    val errorCode = e.toErrorCode()
    Log.w(TAG, "Language split install of ${locale.toLanguageTag()} threw, errorCode=$errorCode", e)
    emit(LanguageInstallStatus.Failed(errorCode))
  }.distinctUntilChanged()

  /** Whether Play refused a request because another download is already running. */
  private fun isBusy(errorCode: Int): Boolean {
    return errorCode == SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED || errorCode == SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION
  }

  /** Play's in-progress session for [locale], if there is one. */
  private suspend fun activeSession(locale: Locale): SplitInstallSessionState? {
    val states = try {
      manager.sessionStates.await()
    } catch (e: Exception) {
      currentCoroutineContext().ensureActive()
      Log.w(TAG, "Failed to read Play's sessions", e)
      return null
    }
    return states.firstOrNull {
      it.toStatus()?.isFinal == false && it.languages().any { language -> LanguageString.matches(language, locale) }
    }
  }

  private fun Throwable.toErrorCode(): Int {
    return (this as? SplitInstallException)?.errorCode ?: SplitInstallErrorCode.INTERNAL_ERROR
  }

  /** Null while Play is canceling, which always ends in [SplitInstallSessionStatus.CANCELED], and for statuses this version doesn't know. */
  private fun SplitInstallSessionState.toStatus(): LanguageInstallStatus? {
    return when (status()) {
      SplitInstallSessionStatus.PENDING,
      SplitInstallSessionStatus.DOWNLOADING,
      SplitInstallSessionStatus.DOWNLOADED,
      SplitInstallSessionStatus.INSTALLING -> LanguageInstallStatus.Downloading(totalBytesToDownload().bytes)
      SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> LanguageInstallStatus.NeedsPlayConfirmation(sessionId())
      SplitInstallSessionStatus.INSTALLED -> LanguageInstallStatus.Installed
      SplitInstallSessionStatus.CANCELED -> LanguageInstallStatus.Cancelled
      SplitInstallSessionStatus.FAILED,
      SplitInstallSessionStatus.UNKNOWN -> LanguageInstallStatus.Failed(errorCode())
      else -> null
    }
  }

  private val LanguageInstallStatus.isFinal: Boolean
    get() = this is LanguageInstallStatus.Installed || this is LanguageInstallStatus.Cancelled || this is LanguageInstallStatus.Failed

  /** Null for an app that is not installed. An app removed for the current user looks the same as one that was never installed. */
  private fun PackageManager.isEnabled(packageName: String): Boolean? {
    return try {
      getApplicationInfo(packageName, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
      null
    }
  }

  private class StartupInstall(val locale: Locale, val job: Job)
}

/** Runs [action] now if the app is in the foreground, otherwise the next time it comes to the foreground. */
private fun runWhenForegrounded(action: () -> Unit) {
  AppForegroundObserver.addListener(object : AppForegroundObserver.Listener {
    private val ran = AtomicBoolean(false)

    override fun onForeground() {
      AppForegroundObserver.removeListener(this)
      if (ran.compareAndSet(false, true)) {
        action()
      }
    }
  })
}
