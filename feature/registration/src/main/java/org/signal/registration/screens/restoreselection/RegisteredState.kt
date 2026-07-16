/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

/**
 * Just an enum for letting the view model know what the registered status is so it can navigate appropriately.
 */
enum class RegisteredState {
  NotRegistered, RegisteredAndPinUnknown, RegisteredAndPinKnown
}
