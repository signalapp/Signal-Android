/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.account

/**
 * The result the two-factor name screen hands back to account settings once a second factor of [kind] has its name,
 * either because it was just set up or because it was [renamed].
 */
data class TwoFactorMethodNamed(val kind: TwoFactorMethod.Kind, val renamed: Boolean)
