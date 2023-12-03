/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.util

import java.util.UUID

fun UUID.toByteArray(): ByteArray = UuidUtil.toByteArray(this)
