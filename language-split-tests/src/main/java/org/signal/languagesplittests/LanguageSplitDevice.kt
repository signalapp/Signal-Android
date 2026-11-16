/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.languagesplittests

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.content.res.Resources
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Installs and drives the app under test from the shell, the way a person would with adb. The app's split APKs, as
 * `bundletool build-apks` names them (`base-master.apk`, `base-arm64_v8a.apk`, `base-xxhdpi.apk`, `base-de.apk`), are
 * expected in [apkDir], which Firebase Test Lab fills with `--other-files`.
 *
 * Instrumentation arguments: `languageSplitPackage` (default `org.thoughtcrime.securesms.test_run`) and
 * `languageSplitApks` (default `/data/local/tmp/language-splits`).
 */
object LanguageSplitDevice {

  private const val TAG = "LanguageSplitTests"
  private const val ACTIVITY = "org.thoughtcrime.securesms.testing.LanguageSplitTestActivity"
  private const val LANGUAGE_PREFERENCE = "pref_language"

  /** bundletool appends `_2`, `_3`, ... to the splits of every variant after the first, one variant per SDK range. */
  private val LANGUAGE_SPLIT = Regex("base-([a-z]{2,3})(_\\d+)?\\.apk")
  private val SESSION_ID = Regex("\\[(\\d+)]")

  private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
  private val arguments get() = InstrumentationRegistry.getArguments()

  val packageName: String get() = arguments.getString("languageSplitPackage") ?: "org.thoughtcrime.securesms.test_run"
  val apkDir: String get() = arguments.getString("languageSplitApks") ?: "/data/local/tmp/language-splits"

  val ui: UiDevice get() = UiDevice.getInstance(instrumentation)

  private var logStart: String? = null

  /** Runs [script] with `sh` as the shell user, the same user adb runs as, and returns its combined output. */
  fun shell(script: String): String {
    val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("sh")
    ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write("(\n$script\n) 2>&1\n".toByteArray()) }
    return ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().decodeToString() }
  }

  /** The split APKs in [apkDir]. */
  fun apks(): List<String> {
    return shell("ls $apkDir").lines().map { it.trim() }.filter { it.endsWith(".apk") }
  }

  /**
   * Removes any existing install, then installs every split in [apkDir] that is not a language (base, ABI, density)
   * plus [languages]. [apkDir] holds one device's set, as `bundletool extract-apks` gives it.
   */
  fun installApp(vararg languages: String) {
    shell("pm uninstall $packageName")
    updateApp(*languages)
  }

  /**
   * Replaces the installed app with the splits [installApp] would install, keeping its data, as an update from Play
   * does. Language splits that are not in [languages] are gone afterwards. The app is killed.
   */
  fun updateApp(vararg languages: String) {
    val base = apks().filter { !LANGUAGE_SPLIT.matches(it) }
    install(base + languages.map { languageApk(it) ?: error("No $it split in $apkDir") }, addToExisting = false)
  }

  /** Adds language splits to the installed app, as Play does after a download. The app is killed. */
  fun addLanguages(vararg languages: String) {
    install(languages.map { languageApk(it) ?: error("No $it split in $apkDir") }, addToExisting = true)
  }

  /** The language codes of the language splits installed now. */
  fun installedLanguages(): Set<String> {
    return shell("pm path $packageName").lines()
      .mapNotNull { Regex("split_config\\.([a-z]{2,3})\\.apk").find(it)?.groupValues?.get(1) }
      .toSet()
  }

  /** Stores [language] as the app's chosen language, or "zz" for the device language. Stops the app to do so. */
  fun setChosenLanguage(language: String) {
    stop()
    val file = "shared_prefs/${packageName}_preferences.xml"
    val entry = "<string name=\"$LANGUAGE_PREFERENCE\">$language</string>"
    val output = shell(
      """
      run-as $packageName sh <<'EOF'
      mkdir -p shared_prefs
      if [ -f $file ]; then
        sed -i -e 's|<string name="$LANGUAGE_PREFERENCE">[^<]*</string>||' -e 's|</map>|$entry</map>|' $file
      else
        printf '%s\n' "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>" '<map>' '$entry' '</map>' > $file
      fi
      cat $file
      EOF
      """.trimIndent()
    )
    check(entry in output) { "Could not store the language: $output" }
  }

  fun stop() {
    shell("am force-stop $packageName")
  }

  /** Starts the activity that hosts the appearance settings without registering. */
  fun start() {
    val output = shell("am start -W -n $packageName/$ACTIVITY")
    check("Status: ok" in output) { "Could not start $ACTIVITY: $output" }
  }

  /** The app's [name] string in [locale], read from the APKs installed now. */
  @Suppress("DEPRECATION")
  fun string(name: String, locale: Locale): String {
    val packageManager = instrumentation.targetContext.packageManager
    val resources = packageManager.getResourcesForApplication(packageManager.getApplicationInfo(packageName, 0))
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    val localized = Resources(resources.assets, resources.displayMetrics, configuration)
    return localized.getString(localized.getIdentifier(name, "string", packageName))
  }

  /** Whether [text] appears on screen within [timeout]. */
  fun isShown(text: String, timeout: Duration = 20.seconds): Boolean {
    return ui.wait(Until.hasObject(By.text(text)), timeout.inWholeMilliseconds) == true
  }

  /** Whether [text] is laid out right to left, judged by its position on screen. It must be shown already. */
  fun isRightToLeft(text: String): Boolean {
    val bounds = ui.findObject(By.text(text))?.visibleBounds ?: error("$text is not on screen")
    return bounds.centerX() > ui.displayWidth / 2
  }

  /** Makes [waitForLog] look only at log lines from now on. */
  fun watchLog() {
    logStart = shell("date '+%m-%d %H:%M:%S.000'").trim()
  }

  /** Whether a log line containing [text] appears within [timeout], counting from the last [watchLog]. */
  fun waitForLog(text: String, timeout: Duration = 20.seconds): Boolean {
    val since = logStart ?: error("Call watchLog first")
    val quoted = "'" + text.replace("'", "'\\''") + "'"
    val deadline = TimeSource.Monotonic.markNow() + timeout
    do {
      if (shell("logcat -d -T '$since' | grep -F -c $quoted").trim() != "0") {
        return true
      }
      Thread.sleep(500)
    } while (deadline.hasNotPassedNow())
    return false
  }

  @SuppressLint("LogNotSignal")
  fun log(message: String) {
    Log.i(TAG, message)
  }

  private fun languageApk(language: String): String? {
    return apks().firstOrNull { LANGUAGE_SPLIT.matchEntire(it)?.groupValues?.get(1) == language }
  }

  private fun install(apks: List<String>, addToExisting: Boolean) {
    val create = shell("pm install-create -r -t" + if (addToExisting) " -p $packageName" else "")
    val session = SESSION_ID.find(create)?.groupValues?.get(1) ?: error("Could not create an install session: $create")
    for (apk in apks) {
      val written = shell("pm install-write $session ${apk.removeSuffix(".apk")} $apkDir/$apk")
      check("Success" in written) { "Could not write $apk: $written" }
    }
    val committed = shell("pm install-commit $session")
    check("Success" in committed) { "Could not install $apks: $committed" }
  }
}
