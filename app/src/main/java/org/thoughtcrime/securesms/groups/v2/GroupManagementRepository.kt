package org.thoughtcrime.securesms.groups.v2

import android.content.Context
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupChangeException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.recipients.Recipient
import java.io.IOException

private val TAG: String = Log.tag(GroupManagementRepository::class.java)

/**
 * Single source repository for managing groups.
 */
class GroupManagementRepository @JvmOverloads constructor(private val context: Context = ApplicationDependencies.getApplication()) {

  fun blockJoinRequests(groupId: GroupId.V2, recipient: Recipient): Single<GroupManagementResult> {
    return Single.fromCallable {
      try {
        GroupManager.ban(context, groupId, recipient.id)
        GroupManagementResult.Success
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        GroupManagementResult.Failure(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        GroupManagementResult.Failure(GroupChangeFailureReason.fromException(e))
      }
    }.subscribeOn(Schedulers.io())
  }

  sealed class GroupManagementResult {
    object Success : GroupManagementResult()
    data class Failure(val reason: GroupChangeFailureReason) : GroupManagementResult()
  }
}
