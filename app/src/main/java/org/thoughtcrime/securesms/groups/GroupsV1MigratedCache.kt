/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups

import androidx.annotation.WorkerThread
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.util.LRUCache

/**
 * Cache to keep track of groups we know do not need a migration run on. This is to save time looking for a gv1 group
 * with the expected v2 id.
 */
object GroupsV1MigratedCache {
  private const val MAX_CACHE = 1000

  private val noV1GroupCache = LRUCache<GroupId.V2, Boolean>(MAX_CACHE)

  @JvmStatic
  @WorkerThread
  fun hasV1Group(groupId: GroupId.V2): Boolean {
    return getV1GroupByV2Id(groupId) != null
  }

  @JvmStatic
  @WorkerThread
  fun getV1GroupByV2Id(groupId: GroupId.V2): GroupRecord? {
    synchronized(noV1GroupCache) {
      if (noV1GroupCache.containsKey(groupId)) {
        return null
      }
    }

    val v1Group = SignalDatabase.groups.getGroupV1ByExpectedV2(groupId)
    if (!v1Group.isPresent) {
      synchronized(noV1GroupCache) {
        noV1GroupCache.put(groupId, true)
      }
    }
    return v1Group.orNull()
  }
}
