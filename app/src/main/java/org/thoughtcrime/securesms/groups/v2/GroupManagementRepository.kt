package org.thoughtcrime.securesms.groups.v2

import android.content.Context
import androidx.core.util.Consumer
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.groups.GroupChangeException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.IOException

private val TAG: String = Log.tag(GroupManagementRepository::class.java)

/**
 * Single source repository for managing GV2 groups.
 */
class GroupManagementRepository @JvmOverloads constructor(private val context: Context = ApplicationDependencies.getApplication()) {

  fun addMembers(groupRecipient: Recipient, selected: List<RecipientId>, consumer: Consumer<GroupAddMembersResult>) {
    addMembers(null, groupRecipient, selected, consumer)
  }

  fun addMembers(groupId: GroupId, selected: List<RecipientId>, consumer: Consumer<GroupAddMembersResult>) {
    addMembers(groupId, null, selected, consumer)
  }

  private fun addMembers(potentialGroupId: GroupId?, potentialGroupRecipient: Recipient?, selected: List<RecipientId>, consumer: Consumer<GroupAddMembersResult>) {
    SignalExecutors.UNBOUNDED.execute {
      val groupId: GroupId.Push = potentialGroupId?.requirePush() ?: potentialGroupRecipient!!.requireGroupId().requirePush()

      val recipients = selected.map(Recipient::resolved)
        .filterNot { it.hasServiceId() && it.isRegistered }
        .toList()

      try {
        ContactDiscovery.refresh(context, recipients, false)
        recipients.forEach { Recipient.live(it.id).refresh() }
      } catch (e: IOException) {
        consumer.accept(GroupAddMembersResult.Failure(GroupChangeFailureReason.NETWORK))
      }

      consumer.accept(
        try {
          val toAdd = selected.filter { Recipient.resolved(it).isRegistered }
          if (toAdd.isNotEmpty()) {
            val groupActionResult = GroupManager.addMembers(context, groupId, toAdd)
            GroupAddMembersResult.Success(groupActionResult.addedMemberCount, Recipient.resolvedList(groupActionResult.invitedMembers))
          } else {
            GroupAddMembersResult.Failure(GroupChangeFailureReason.NOT_GV2_CAPABLE)
          }
        } catch (e: Exception) {
          Log.d(TAG, "Failure to add member", e)
          GroupAddMembersResult.Failure(GroupChangeFailureReason.fromException(e))
        }
      )
    }
  }

  fun blockJoinRequests(groupId: GroupId.V2, recipient: Recipient): Single<GroupBlockJoinRequestResult> {
    return Single.fromCallable {
      try {
        GroupManager.ban(context, groupId, recipient.id)
        GroupBlockJoinRequestResult.Success
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        GroupBlockJoinRequestResult.Failure(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        GroupBlockJoinRequestResult.Failure(GroupChangeFailureReason.fromException(e))
      }
    }.subscribeOn(Schedulers.io())
  }
}
