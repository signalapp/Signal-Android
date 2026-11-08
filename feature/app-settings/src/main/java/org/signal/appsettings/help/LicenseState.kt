/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * [LicenseScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class LicenseState(
  /** The open-source license text, one entry per line. Empty until it has been read. */
  val licenseLines: List<String> = emptyList()
) {
  override fun toString(): String = "LicenseState(licenseLines=${licenseLines.size} lines)"
}
