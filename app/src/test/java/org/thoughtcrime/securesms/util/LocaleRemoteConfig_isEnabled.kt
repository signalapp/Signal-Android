/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.thoughtcrime.securesms.assertIs

@RunWith(Parameterized::class)
class LocaleRemoteConfig_isEnabled(private val serializedList: String, private val e164: List<String>, private val output: Boolean) {

  @Test
  fun isLegal() {
    e164.forEach {
      LocaleRemoteConfig.isEnabledE164Start(serializedList, it) assertIs output
    }
  }

  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): Collection<Array<Any>> {
      return listOf(
        arrayOf("1,2,3", listOf("+15555555555", "+25552555555", "+35555555555"), true),
        arrayOf("1 123,2,33 1231", listOf("+112355555555", "+25552555555", "+331231555555"), true),
        arrayOf("1,2,3", listOf("+1 5 5 5 5 55 5555", "+255525 55 555", "+355 55 555555"), true),
        arrayOf("1 123,2,33 1231", listOf("+1 1 2 3 55 555 555", "+ 255-52 5 5 5555", "+3 31 2 3 1 5-5 5 5 5 5"), true),

        arrayOf("5,7,8", listOf("+15555555555", "+25552555555", "+35555555555"), false),
        arrayOf("5 123,5,31 1231", listOf("+112355555555", "+25552555555", "+331231555555"), false),
        arrayOf("5,7,8", listOf("+1 5 5 5 5 55 5555", "+255525 55 555", "+355 55 555555"), false),
        arrayOf("1 125,5,33 5231", listOf("+1 1 2 3 55 555 555", "+ 255 52 5 5 5555", "+3 31 2 3 1 5 5 5-5-5 5"), false)
      )
    }
  }
}
