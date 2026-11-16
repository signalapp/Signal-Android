/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CopyOnWriteArrayList

internal const val SESSION = 7
internal const val OTHER_SESSION = 9

/** A relaxed [SplitInstallManager] mock whose installs start [SESSION] and whose listener tests can drive with [update]. */
internal class SplitInstallManagerFixture {
  val manager: SplitInstallManager = mockk(relaxed = true)

  val listener = slot<SplitInstallStateUpdatedListener>()

  private val listeners = CopyOnWriteArrayList<SplitInstallStateUpdatedListener>()

  init {
    every { manager.registerListener(capture(listener)) } answers { listeners += listener.captured }
    every { manager.unregisterListener(any()) } answers { listeners -= firstArg<SplitInstallStateUpdatedListener>() }
    every { manager.installedLanguages } returns emptySet()
    every { manager.startInstall(any()) } returns Tasks.forResult(SESSION)
  }

  fun update(status: Int, sessionId: Int = SESSION, errorCode: Int = 0) {
    val state = sessionState(status, sessionId, errorCode)
    listeners.forEach { it.onStateUpdate(state) }
  }
}

/** Play refuses to build a confirmation state without a real resolution intent, so that one is mocked. */
internal fun sessionState(
  status: Int,
  sessionId: Int = SESSION,
  errorCode: Int = 0,
  languages: List<String> = listOf("de")
): SplitInstallSessionState {
  if (status == SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION) {
    return mockk {
      every { sessionId() } returns sessionId
      every { status() } returns status
      every { errorCode() } returns errorCode
      every { languages() } returns languages
    }
  }
  return SplitInstallSessionState.create(sessionId, status, errorCode, 0, 0, emptyList(), languages)
}

/** Play delivers task results on the main thread. */
internal fun idleMainLooper() {
  shadowOf(Looper.getMainLooper()).idle()
}

internal fun TestScope.collect(flow: Flow<LanguageInstallStatus>): List<LanguageInstallStatus> {
  val states = mutableListOf<LanguageInstallStatus>()
  backgroundScope.launch { flow.toList(states) }
  return states
}

/** Replaces the app's own package with one that has these config splits. No splits at all is a universal APK. */
internal fun installSplits(vararg splits: String) {
  val context = ApplicationProvider.getApplicationContext<Context>()
  shadowOf(context.packageManager).installPackage(
    PackageInfo().apply {
      packageName = context.packageName
      applicationInfo = ApplicationInfo().apply {
        packageName = context.packageName
        splitSourceDirs = splits.map { "/data/app/pkg/split_config.$it.apk" }.toTypedArray()
      }
    }
  )
}

internal fun installPackage(packageName: String, enabled: Boolean) {
  val context = ApplicationProvider.getApplicationContext<Context>()
  shadowOf(context.packageManager).installPackage(
    PackageInfo().apply {
      this.packageName = packageName
      applicationInfo = ApplicationInfo().apply {
        this.packageName = packageName
        this.enabled = enabled
      }
    }
  )
}

/** Starts [AppLocale] with the splits that are installed right now, under the system default. */
internal fun initializeAppLocale(vararg supportedLanguages: String) {
  val context = ApplicationProvider.getApplicationContext<Context>()
  context.applicationInfo.splitSourceDirs = context.packageManager.getApplicationInfo(context.packageName, 0).splitSourceDirs
  AppLocale.initialize(context, arrayOf(*supportedLanguages), languageSplits = true) { "zz" }
}
