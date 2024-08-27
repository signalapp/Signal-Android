package org.thoughtcrime.securesms.groups.v2.processing

import org.signal.libsignal.zkgroup.groupsend.GroupSendEndorsementsResponse
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog

/**
 * Combination of Local and Server group state.
 */
class GroupStateDiff(
  val previousGroupState: DecryptedGroup?,
  val serverHistory: List<DecryptedGroupChangeLog>,
  val groupSendEndorsementsResponse: GroupSendEndorsementsResponse?
) {

  constructor(
    previousGroupState: DecryptedGroup?,
    changedGroupState: DecryptedGroup?,
    change: DecryptedGroupChange?
  ) : this(previousGroupState, listOf(DecryptedGroupChangeLog(changedGroupState, change)), null)

  val earliestRevisionNumber: Int
    get() {
      if (previousGroupState != null) {
        return previousGroupState.revision
      } else {
        if (serverHistory.isEmpty()) {
          throw AssertionError()
        }
        return serverHistory[0].revision
      }
    }

  val latestRevisionNumber: Int
    get() {
      if (serverHistory.isEmpty()) {
        if (previousGroupState == null) {
          throw AssertionError()
        }
        return previousGroupState.revision
      }
      return serverHistory[serverHistory.size - 1].revision
    }
}
