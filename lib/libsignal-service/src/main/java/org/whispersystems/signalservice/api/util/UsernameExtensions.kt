/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.util

import org.signal.libsignal.usernames.Username

val Username.nickname: String get() = username.split(Usernames.DELIMITER)[0]
val Username.discriminator: String get() = username.split(Usernames.DELIMITER)[1]
