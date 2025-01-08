/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import assertk.Assert
import assertk.assertions.isFalse
import java.util.Optional

fun <T> Assert<Optional<T>>.isAbsent() {
  transform { it.isPresent }.isFalse()
}
