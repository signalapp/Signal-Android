/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.FileInputStream

/**
 * Disables system animation scales for the duration of a test and restores them afterward.
 *
 * Espresso and [android.app.Instrumentation.waitForIdleSync] only make progress once the main looper is idle. An
 * on-screen indeterminate animation (e.g. the checkout's `CircularProgressIndicator`) posts frame callbacks forever, so
 * the looper never idles and any idle-gated wait hangs indefinitely rather than timing out. Forcing the scales to 0
 * stops those animations so the looper can idle.
 *
 * Writes go through the shell (`UiAutomation`), which holds `WRITE_SECURE_SETTINGS`; the app process does not.
 */
class DisableAnimationsRule : ExternalResource() {

  private val scales = listOf(
    Settings.Global.WINDOW_ANIMATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.ANIMATOR_DURATION_SCALE
  )

  private lateinit var previous: Map<String, Float>

  override fun before() {
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    previous = scales.associateWith { Settings.Global.getFloat(resolver, it, 1f) }
    scales.forEach { putScale(it, 0f) }
  }

  override fun after() {
    if (::previous.isInitialized) {
      previous.forEach { (key, value) -> putScale(key, value) }
    }
  }

  private fun putScale(key: String, value: Float) {
    val stream = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put global $key $value")
    FileInputStream(stream.fileDescriptor).use { it.readBytes() }
  }
}
