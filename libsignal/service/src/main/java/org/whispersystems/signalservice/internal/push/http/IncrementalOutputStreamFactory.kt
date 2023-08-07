/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.http

import org.whispersystems.signalservice.api.crypto.DigestingOutputStream
import java.io.OutputStream

interface IncrementalOutputStreamFactory : OutputStreamFactory {

  override fun createFor(wrap: OutputStream?): DigestingOutputStream = error("Use createIncrementalFor instead.")

  fun createIncrementalFor(wrap: OutputStream?, length: Long, incrementalDigestOut: OutputStream?): DigestingOutputStream
}
