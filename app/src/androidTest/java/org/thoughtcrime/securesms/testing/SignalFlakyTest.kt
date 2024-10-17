/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing

@Retention(AnnotationRetention.RUNTIME)
annotation class SignalFlakyTest(val allowedAttempts: Int = 3)
