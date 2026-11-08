/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import kotlinx.coroutines.withContext
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.io.InputStream

/**
 * Reads the bundled open-source license text behind [LicenseViewModel].
 */
object LicenseRepository {

  suspend fun getLicenseLines(): List<String> = withContext(SignalDispatchers.Default) {
    val context = AppDependencies.application

    context.resources.openRawResource(R.raw.third_party_licenses).readToLines() +
      context.assets.open("acknowledgments/libsignal.md").readToLines() +
      context.assets.open("acknowledgments/ringrtc.md").readToLines()
  }

  private fun InputStream.readToLines(): List<String> {
    return bufferedReader().use { it.readText().split("\n") }
  }
}
