/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.SignalProgressDialog
import org.thoughtcrime.securesms.groups.GroupChangeBusyException
import org.thoughtcrime.securesms.groups.GroupChangeException
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupManager
import java.io.IOException

/**
 * Handles the end group flow for admins. Shows a two-step confirmation
 * dialog before terminating the group.
 */
object EndGroupDialog {

  private val TAG = Log.tag(EndGroupDialog::class.java)

  @JvmStatic
  fun show(activity: FragmentActivity, groupId: GroupId.V2, groupName: String) {
    MaterialAlertDialogBuilder(activity)
      .setTitle(activity.getString(R.string.EndGroupDialog__end_s, groupName))
      .setMessage(R.string.EndGroupDialog__members_will_no_longer_be_able_to_send)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.EndGroupDialog__end_group) { _, _ ->
        showFinalConfirmation(activity, groupId)
      }
      .show()
  }

  private fun showFinalConfirmation(activity: FragmentActivity, groupId: GroupId.V2) {
    MaterialAlertDialogBuilder(activity)
      .setMessage(R.string.EndGroupDialog__this_will_end_the_group_permanently)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.EndGroupDialog__end_group) { _, _ ->
        performEndGroup(activity, groupId)
      }
      .show()
  }

  private fun performEndGroup(activity: FragmentActivity, groupId: GroupId.V2) {
    val progressDialog = SignalProgressDialog.show(
      context = activity,
      message = activity.getString(R.string.EndGroupDialog__ending_group),
      indeterminate = true
    )

    activity.lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        try {
          GroupManager.terminateGroup(activity, groupId)
          GroupChangeResult.SUCCESS
        } catch (e: GroupChangeException) {
          Log.w(TAG, "Failed to end group", e)
          GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
        } catch (e: GroupChangeBusyException) {
          Log.w(TAG, "Failed to end group", e)
          GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
        } catch (e: IOException) {
          Log.w(TAG, "Failed to end group", e)
          GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
        }
      }
      progressDialog.dismiss()
      if (!result.isSuccess) {
        showRetryDialog(activity, groupId)
      }
    }
  }

  private fun showRetryDialog(activity: FragmentActivity, groupId: GroupId.V2) {
    MaterialAlertDialogBuilder(activity)
      .setMessage(R.string.EndGroupDialog__ending_the_group_failed)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.EndGroupDialog__try_again) { _, _ ->
        performEndGroup(activity, groupId)
      }
      .show()
  }
}
