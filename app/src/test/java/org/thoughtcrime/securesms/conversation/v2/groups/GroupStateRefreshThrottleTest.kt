/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.groups

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.After
import org.junit.Test
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.groups.GroupId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class GroupStateRefreshThrottleTest {

  private val groupA = GroupId.v2(GroupMasterKey(ByteArray(32) { 1 }))
  private val groupB = GroupId.v2(GroupMasterKey(ByteArray(32) { 2 }))

  @After
  fun tearDown() {
    GroupStateRefreshThrottle.clear()
  }

  @Test
  fun `first refresh is allowed`() {
    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)).isTrue()
  }

  @Test
  fun `refresh within throttle window is skipped`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)).isFalse()
    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000 + 5.minutes.inWholeMilliseconds - 1)).isFalse()
  }

  @Test
  fun `refresh after throttle window is allowed`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000 + 5.minutes.inWholeMilliseconds)).isTrue()
  }

  @Test
  fun `skipped refresh does not extend the throttle window`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000 + 4.minutes.inWholeMilliseconds)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000 + 5.minutes.inWholeMilliseconds)).isTrue()
  }

  @Test
  fun `groups are throttled independently`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupB, 5.minutes, now = 1000)).isTrue()
  }

  @Test
  fun `shorter throttle duration allows refresh sooner`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 30.seconds, now = 1000)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 30.seconds, now = 1000 + 29.seconds.inWholeMilliseconds)).isFalse()
    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 30.seconds, now = 1000 + 30.seconds.inWholeMilliseconds)).isTrue()
  }

  @Test
  fun `clearing a group allows refresh within throttle window`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)
    GroupStateRefreshThrottle.shouldRefresh(groupB, 5.minutes, now = 1000)

    GroupStateRefreshThrottle.clear(groupA)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 2000)).isTrue()
    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupB, 5.minutes, now = 2000)).isFalse()
  }

  @Test
  fun `clock moving backwards allows refresh`() {
    GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000 + 10.seconds.inWholeMilliseconds)

    assertThat(GroupStateRefreshThrottle.shouldRefresh(groupA, 5.minutes, now = 1000)).isTrue()
  }
}
