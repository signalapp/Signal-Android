/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

/**
 * The terminal restore decision the user reached during registration. Mirrors the terminal states of the app's
 * RestoreDecisionState. We intentionally do not model the transient pending states (START / INTEND_TO_RESTORE)
 * here, as the registration flow performs any restore inline before completing.
 */
enum class RestoreDecision {
  NEW_ACCOUNT,
  SKIPPED,
  COMPLETED
}
