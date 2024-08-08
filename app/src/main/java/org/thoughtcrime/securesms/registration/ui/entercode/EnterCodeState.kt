/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

data class EnterCodeState(val resetRequiredAfterFailure: Boolean = false, val showKeyboard: Boolean = false)
