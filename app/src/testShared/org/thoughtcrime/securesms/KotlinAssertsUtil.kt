/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers

fun <T : Any?> T.assertIsNull() {
  MatcherAssert.assertThat(this, Matchers.nullValue())
}

fun <T : Any?> T.assertIsNotNull() {
  MatcherAssert.assertThat(this, Matchers.notNullValue())
}

infix fun <T : Any?> T.assertIs(expected: T) {
  MatcherAssert.assertThat(this, Matchers.`is`(expected))
}

infix fun <T : Any> T.assertIsNot(expected: T) {
  MatcherAssert.assertThat(this, Matchers.not(Matchers.`is`(expected)))
}

infix fun <E, T : Collection<E>> T.assertIsSize(expected: Int) {
  MatcherAssert.assertThat(this, Matchers.hasSize(expected))
}
