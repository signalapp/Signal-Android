/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("ProtoUtil")

import okio.ByteString

object ProtoUtil {

  fun ByteString?.isNotEmpty(): Boolean {
    return this != null && this.size > 0
  }

  fun ByteString?.isNullOrEmpty(): Boolean {
    return this == null || this.size == 0
  }
}
