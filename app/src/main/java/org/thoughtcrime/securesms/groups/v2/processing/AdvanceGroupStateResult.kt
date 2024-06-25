package org.thoughtcrime.securesms.groups.v2.processing

import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupChangeLog

/**
 * Result of applying group state changes to a local group state.
 *
 * @param updatedGroupState cumulative result of applying changes to the input group state
 * @param processedLogEntries Local view of logs/changes applied from input group state to [updatedGroupState]
 * @param remainingRemoteGroupChanges Remote view of logs/changes yet to be applied to the local [updatedGroupState]
 */
data class AdvanceGroupStateResult @JvmOverloads constructor(
  val updatedGroupState: DecryptedGroup?,
  val processedLogEntries: Collection<AppliedGroupChangeLog> = emptyList(),
  val remainingRemoteGroupChanges: List<DecryptedGroupChangeLog> = emptyList()
)
