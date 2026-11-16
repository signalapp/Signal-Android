/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.incommon

import org.thoughtcrime.securesms.groups.GroupInCommon

/**
 * [GroupsInCommonScreen] state.
 */
data class GroupsInCommonState(val groups: List<GroupInCommon> = emptyList())
