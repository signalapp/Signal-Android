/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push

data class ProfileAvatarUploadAttributes(
  val key: String = "",
  val credential: String = "",
  val acl: String = "",
  val algorithm: String = "",
  val date: String = "",
  val policy: String = "",
  val signature: String = ""
)
