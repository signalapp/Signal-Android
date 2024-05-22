package org.thoughtcrime.securesms.groups.v2.processing

import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange

/**
 * Pair of a group state and optionally the corresponding change.
 *
 * Similar to [org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog] but guaranteed to have a group state.
 *
 * Changes are typically not available for pending members.
 */
data class AppliedGroupChangeLog internal constructor(val group: DecryptedGroup, val change: DecryptedGroupChange?) {
  init {
    if (change != null && group.revision != change.revision) {
      throw AssertionError()
    }
  }
}
