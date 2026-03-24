/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push.exceptions

/**
 * Wraps a [org.signal.libsignal.net.RequestResult.ApplicationError]'s cause in a named
 * [RuntimeException] so it is more identifiable.
 */
class UnknownGroupSendException(cause: Throwable) : RuntimeException(cause)
