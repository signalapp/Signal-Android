/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

/**
 * Status codes for the ArchiveMediaUploadForm endpoint.
 *
 * Kept in a separate class because [AttachmentUploadForm] (the model the request returns) is used for multiple endpoints with different status codes.
 */
enum class ArchiveMediaUploadFormStatusCodes(val code: Int) {
  BadArguments(400),
  InvalidPresentationOrSignature(401),
  InsufficientPermissions(403),
  RateLimited(429),
  Unknown(-1);

  companion object {
    fun from(code: Int): ArchiveMediaUploadFormStatusCodes {
      return entries.firstOrNull { it.code == code } ?: Unknown
    }
  }
}
