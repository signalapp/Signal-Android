package org.whispersystems.signalservice.api.groupsv2

import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange

/**
 * A changelog from the server representing a specific group state revision. The
 * log can contain:
 *
 * 1. A full group snapshot for the revision
 * 2. A full group snapshot and the change from the previous revision to achieve the snapshot
 * 3. Only the change from the previous revision to achieve this revision
 *
 * Most often, it will be the change only (3).
 */
data class DecryptedGroupChangeLog(val group: DecryptedGroup?, val change: DecryptedGroupChange?) {

  val revision: Int
    get() = group?.revision ?: change!!.revision

  init {
    if (group == null && change == null) {
      throw InvalidGroupStateException("group and change are both null")
    }

    if (group != null && change != null && group.revision != change.revision) {
      throw InvalidGroupStateException("group revision != change revision")
    }
  }
}
