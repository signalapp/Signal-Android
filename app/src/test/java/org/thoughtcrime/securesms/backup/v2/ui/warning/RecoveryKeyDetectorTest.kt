/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.warning

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.signal.core.models.AccountEntropyPool

class RecoveryKeyDetectorTest {

  private val recoveryKey = AccountEntropyPool.generate()

  @Test
  fun `null recovery key is never detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey(recoveryKey.value, null)).isFalse()
  }

  @Test
  fun `null or blank text is not a recovery key`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey(null, recoveryKey)).isFalse()
    assertThat(RecoveryKeyDetector.containsRecoveryKey("", recoveryKey)).isFalse()
    assertThat(RecoveryKeyDetector.containsRecoveryKey("       ", recoveryKey)).isFalse()
  }

  @Test
  fun `ordinary message is not a recovery key`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey("Hey, are we still on for lunch tomorrow?", recoveryKey)).isFalse()
  }

  @Test
  fun `text shorter than a key is not a recovery key`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey(recoveryKey.value.dropLast(1), recoveryKey)).isFalse()
  }

  @Test
  fun `exact storage form is detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey(recoveryKey.value, recoveryKey)).isTrue()
  }

  @Test
  fun `uppercase form is detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey(recoveryKey.value.uppercase(), recoveryKey)).isTrue()
  }

  @Test
  fun `display form with substitution characters is detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey(recoveryKey.displayValue, recoveryKey)).isTrue()
  }

  @Test
  fun `grouped with spaces is detected`() {
    val grouped = recoveryKey.value.chunked(4).joinToString("  ")
    assertThat(RecoveryKeyDetector.containsRecoveryKey(grouped, recoveryKey)).isTrue()
  }

  @Test
  fun `embedded in surrounding text is detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey("Hey, here is my recovery key: ${recoveryKey.value} keep it safe!", recoveryKey)).isTrue()
  }

  @Test
  fun `embedded grouped display form is detected`() {
    val grouped = recoveryKey.displayValue.chunked(4).joinToString(" ")
    assertThat(RecoveryKeyDetector.containsRecoveryKey("my key\n$grouped\nthanks", recoveryKey)).isTrue()
  }

  @Test
  fun `a different valid recovery key is not detected`() {
    val otherKey = AccountEntropyPool.generate()
    assertThat(RecoveryKeyDetector.containsRecoveryKey(otherKey.value, recoveryKey)).isFalse()
  }

  // These would be false positives under shape-only matching (charset + length, no checksum). Matching
  // against the user's actual key rejects them.

  @Test
  fun `64 in-alphabet characters that are not the key are not detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey("g".repeat(64), recoveryKey)).isFalse()
  }

  @Test
  fun `64 char sha256 hex digest is not detected`() {
    assertThat(RecoveryKeyDetector.containsRecoveryKey("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", recoveryKey)).isFalse()
  }
}
