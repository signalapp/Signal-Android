/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.groupsv2

import org.signal.libsignal.zkgroup.groups.GroupIdentifier

/**
 * Details what verification should take place when decrypting a group change.
 *
 * @param verify Should perform group change verification during decryption
 * @param groupId The id this change should apply to and will be verified is set in change payload
 */
class DecryptChangeVerificationMode private constructor(
  @get:JvmName("verify") val verify: Boolean,
  @get:JvmName("groupId") val groupId: GroupIdentifier?
) {
  companion object {

    /**
     * Use when the changes are already trusted. This would be during group creation or when fetching
     * group changes directly from the server.
     */
    @JvmStatic
    @get:JvmName("alreadyTrusted")
    val alreadyTrusted by lazy { DecryptChangeVerificationMode(verify = false, groupId = null) }

    /**
     * Use when the changes are from an untrusted source like a P2P message of a group change. This
     * will verify the signature and group id match.
     */
    @JvmStatic
    fun verify(groupId: GroupIdentifier): DecryptChangeVerificationMode {
      return DecryptChangeVerificationMode(verify = true, groupId = groupId)
    }
  }
}
