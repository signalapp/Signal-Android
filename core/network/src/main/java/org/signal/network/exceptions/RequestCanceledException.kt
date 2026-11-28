/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.network.exceptions

import java.io.IOException

/** A request was explicitly canceled by the caller. */
class RequestCanceledException : IOException("Canceled!")
