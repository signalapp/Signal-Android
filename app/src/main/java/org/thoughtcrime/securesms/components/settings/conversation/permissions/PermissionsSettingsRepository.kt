package org.thoughtcrime.securesms.components.settings.conversation.permissions

import android.content.Context
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupChangeException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.ui.GroupChangeErrorCallback
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import java.io.IOException

private val TAG = Log.tag(PermissionsSettingsRepository::class.java)

class PermissionsSettingsRepository(private val context: Context) {

  fun applyMembershipRightsChange(groupId: GroupId, newRights: GroupAccessControl, error: GroupChangeErrorCallback) {
    SignalExecutors.UNBOUNDED.execute {
      try {
        GroupManager.applyMembershipAdditionRightsChange(context, groupId.requireV2(), newRights)
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        error.onError(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        error.onError(GroupChangeFailureReason.fromException(e))
      }
    }
  }

  fun applyAttributesRightsChange(groupId: GroupId, newRights: GroupAccessControl, error: GroupChangeErrorCallback) {
    SignalExecutors.UNBOUNDED.execute {
      try {
        GroupManager.applyAttributesRightsChange(context, groupId.requireV2(), newRights)
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        error.onError(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        error.onError(GroupChangeFailureReason.fromException(e))
      }
    }
  }

  fun applyAnnouncementGroupChange(groupId: GroupId, isAnnouncementGroup: Boolean, error: GroupChangeErrorCallback) {
    SignalExecutors.UNBOUNDED.execute {
      try {
        GroupManager.applyAnnouncementGroupChange(context, groupId.requireV2(), isAnnouncementGroup)
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        error.onError(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        error.onError(GroupChangeFailureReason.fromException(e))
      }
    }
  }
}
