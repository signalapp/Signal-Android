/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.twofactorverify

/** The kinds of second factor the reminder can verify, which is also what it reports back once one checks out. */
enum class TwoFactorVerifyMethod {
  AUTHENTICATOR_APP,
  PASSKEY
}
