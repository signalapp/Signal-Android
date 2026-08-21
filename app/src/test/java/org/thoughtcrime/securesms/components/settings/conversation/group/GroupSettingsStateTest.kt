/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.group

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.thoughtcrime.securesms.groups.GroupId

class GroupSettingsStateTest {

  private val v2GroupId = GroupId.v2(GroupMasterKey(ByteArray(GroupMasterKey.SIZE)))
  private val v1GroupId = GroupId.v1(ByteArray(16))
  private val mmsGroupId = GroupId.mms(ByteArray(16))

  private fun createState(
    groupId: GroupId = v2GroupId,
    isActive: Boolean = true,
    isSelfAdmin: Boolean = true,
    isAnnouncementGroup: Boolean = false
  ): GroupSettingsState {
    return GroupSettingsState(
      groupId = groupId,
      isActive = isActive,
      isSelfAdmin = isSelfAdmin,
      isAnnouncementGroup = isAnnouncementGroup
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

  @Test
  fun `canLeave is true for an active push group`() {
    assertTrue(createState().canLeave)
  }

  @Test
  fun `canLeave is true for an active v1 group`() {
    assertTrue(createState(groupId = v1GroupId).canLeave)
  }

  @Test
  fun `canLeave is false for an mms group`() {
    assertFalse(createState(groupId = mmsGroupId).canLeave)
  }

  @Test
  fun `canLeave is false for an inactive group`() {
    assertFalse(createState(isActive = false).canLeave)
  }

  @Test
  fun `isAnnouncementGroupRestricted is true for non-admins of an announcement group`() {
    assertTrue(createState(isAnnouncementGroup = true, isSelfAdmin = false).isAnnouncementGroupRestricted)
  }

  @Test
  fun `isAnnouncementGroupRestricted is false for admins of an announcement group`() {
    assertFalse(createState(isAnnouncementGroup = true, isSelfAdmin = true).isAnnouncementGroupRestricted)
  }

  @Test
  fun `isAnnouncementGroupRestricted is false for a normal group`() {
    assertFalse(createState(isAnnouncementGroup = false, isSelfAdmin = false).isAnnouncementGroupRestricted)
  }
}
