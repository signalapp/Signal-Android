/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.groups.GroupId

class GroupSettingsStateTest {

  private val v2GroupId = GroupId.v2(org.signal.libsignal.zkgroup.groups.GroupMasterKey(ByteArray(32)))
  private val v1GroupId = GroupId.v1(ByteArray(16))

  private fun createState(
    groupId: GroupId = v2GroupId,
    isActive: Boolean = true,
    isSelfAdmin: Boolean = true,
    canLeave: Boolean = true
  ): SpecificSettingsState.GroupSettingsState {
    return SpecificSettingsState.GroupSettingsState(
      groupId = groupId,
      isActive = isActive,
      isSelfAdmin = isSelfAdmin,
      canLeave = canLeave
    )
  }

  @Test
  fun `canEndGroup is true when active v2 group and self is admin`() {
    assertTrue(createState().canEndGroup)
  }

  @Test
  fun `canEndGroup is false when group is not active`() {
    assertFalse(createState(isActive = false).canEndGroup)
  }

  @Test
  fun `canEndGroup is false when self is not admin`() {
    assertFalse(createState(isSelfAdmin = false).canEndGroup)
  }

  @Test
  fun `canEndGroup is false for v1 group`() {
    assertFalse(createState(groupId = v1GroupId).canEndGroup)
  }
}
