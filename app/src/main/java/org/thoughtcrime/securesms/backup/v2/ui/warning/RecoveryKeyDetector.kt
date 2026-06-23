/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.warning

import org.signal.core.models.AccountEntropyPool

/**
 * Detects whether a block of text contains the user's own [AccountEntropyPool] (recovery key).
 *
 * We scan anywhere within the text and try to match the key in as many forms as possible:
 * upper/lowercase, with or without grouping spaces, and with or without the display characters
 * (e.g. '#'/'=') used to disambiguate 'O'/'0'. Matching against the user's actual key (rather than
 * just the AEP shape) avoids false positives on any 64-character in-alphabet string.
 */
object RecoveryKeyDetector {

  /**
   * @param text the text to scan
   * @param recoveryKey the user's own recovery key, or null if they don't have one yet
   * @return true if [text] contains [recoveryKey] in any of its accepted forms. Always false when
   * [recoveryKey] is null, so callers can bypass the check entirely for users without a key.
   */
  fun containsRecoveryKey(text: String?, recoveryKey: AccountEntropyPool?): Boolean {
    if (recoveryKey == null || text.isNullOrBlank() || text.length < AccountEntropyPool.LENGTH) {
      return false
    }

    val normalized = AccountEntropyPool.removeIllegalCharacters(AccountEntropyPool.formatForStorage(text)).lowercase()

    return normalized.contains(recoveryKey.value)
  }
}
