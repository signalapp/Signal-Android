/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push.exceptions

import java.io.IOException

/**
 * Wraps an exception that does not hold retry after data so that it *can* have retry after data.
 */
class RetryNetworkException(val retryAfterMs: Long, cause: Throwable) : IOException(cause)
