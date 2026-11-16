/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.language

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.signal.locale.AppLocale
import org.signal.locale.LanguageAvailability
import org.thoughtcrime.securesms.dependencies.AppDependencies

/**
 * Preconditions and shell access shared by the language split device tests. A test whose install is not set up skips,
 * or fails when the run passes `-e requireLanguageSplitSetup true`.
 */
object LanguageSplitTestSetup {

  private const val REQUIRE_SETUP = "requireLanguageSplitSetup"

  fun require(message: String, condition: Boolean) {
    if (InstrumentationRegistry.getArguments().getString(REQUIRE_SETUP).toBoolean()) {
      assertTrue(message, condition)
    } else {
      assumeTrue(message, condition)
    }
  }

  fun requireSplitInstall() {
    require("Not a language split install", AppLocale.isSplitInstall)
  }

  /** Requires [language] to be missing with Play able to deliver it. */
  fun requireDownloadable(language: String) {
    val availability = AppDependencies.languageSplitRepository.availability(language)
    require("$language is $availability, not downloadable", availability == LanguageAvailability.NeedsDownload)
  }

  /** Runs [command] as the shell user and waits for it to finish. */
  fun shell(command: String): String {
    val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes().decodeToString() }
  }
}
