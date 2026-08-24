/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.content.Context

/**
 * Controller to let users contact support with a debug log
 */
interface ContactSupportController {

  suspend fun uploadDebugLog(): String?

  fun sendSupportEmail(context: Context, subject: String, filter: String, debugLogUrl: String?)
}
