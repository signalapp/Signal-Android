package org.thoughtcrime.securesms.groups.ui

import org.signal.core.util.groups.GroupChangeBusyException
import org.signal.core.util.groups.GroupInsufficientRightsException
import org.signal.core.util.groups.GroupNotAMemberException
import org.signal.core.util.groups.MembershipNotSuitableForV2Exception
import org.whispersystems.signalservice.internal.push.exceptions.GroupTerminatedException
import java.io.IOException

enum class GroupChangeFailureReason {
  NO_RIGHTS,
  NOT_GV2_CAPABLE,
  NOT_ANNOUNCEMENT_CAPABLE,
  NOT_A_MEMBER,
  BUSY,
  NETWORK,
  GROUP_TERMINATED,
  OTHER;

  companion object {
    @JvmStatic
    fun fromException(e: Throwable): GroupChangeFailureReason {
      if (e is MembershipNotSuitableForV2Exception) return GroupChangeFailureReason.NOT_GV2_CAPABLE
      if (e is GroupTerminatedException) return GroupChangeFailureReason.GROUP_TERMINATED
      if (e is IOException) return GroupChangeFailureReason.NETWORK
      if (e is GroupNotAMemberException) return GroupChangeFailureReason.NOT_A_MEMBER
      if (e is GroupChangeBusyException) return GroupChangeFailureReason.BUSY
      if (e is GroupInsufficientRightsException) return GroupChangeFailureReason.NO_RIGHTS
      return GroupChangeFailureReason.OTHER
    }
  }
}
