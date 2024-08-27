/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <T : Any?> T.assertIsNull() {
  contract {
    returns() implies (this@assertIsNull == null)
  }
  MatcherAssert.assertThat(this, Matchers.nullValue())
}

@OptIn(ExperimentalContracts::class)
fun <T : Any?> T.assertIsNotNull() {
  contract {
    returns() implies (this@assertIsNotNull != null)
  }
  MatcherAssert.assertThat(this, Matchers.notNullValue())
}

infix fun <T : Any?> T.assertIs(expected: T) {
  MatcherAssert.assertThat(this, Matchers.`is`(expected))
}

infix fun <T : Any?> T.assertIsNot(expected: T) {
  MatcherAssert.assertThat(this, Matchers.not(Matchers.`is`(expected)))
}

infix fun <E, T : Collection<E>> T.assertIsSize(expected: Int) {
  MatcherAssert.assertThat(this, Matchers.hasSize(expected))
}
