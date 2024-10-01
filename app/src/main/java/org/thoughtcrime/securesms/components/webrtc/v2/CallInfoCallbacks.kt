/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.core.app.ShareCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragmentArgs
import org.thoughtcrime.securesms.components.webrtc.controls.CallInfoView
import org.thoughtcrime.securesms.components.webrtc.controls.ControlsAndInfoViewModel
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant

/**
 * Callbacks for the CallInfoView, shared between CallActivity and ControlsAndInfoController.
 */
class CallInfoCallbacks(
  private val activity: BaseActivity,
  private val controlsAndInfoViewModel: ControlsAndInfoViewModel
) : CallInfoView.Callbacks {

  override fun onShareLinkClicked() {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(activity)
      .setText(CallLinks.url(controlsAndInfoViewModel.rootKeySnapshot))
      .setType(mimeType)
      .createChooserIntent()

    try {
      activity.startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }

  override fun onEditNameClicked(name: String) {
    EditCallLinkNameDialogFragment().apply {
      arguments = EditCallLinkNameDialogFragmentArgs.Builder(name).build().toBundle()
    }.show(activity.supportFragmentManager, null)
  }

  override fun onBlock(callParticipant: CallParticipant) {
    MaterialAlertDialogBuilder(activity)
      .setNegativeButton(android.R.string.cancel, null)
      .setMessage(activity.resources.getString(R.string.CallLinkInfoSheet__remove_s_from_the_call, callParticipant.recipient.getShortDisplayName(activity)))
      .setPositiveButton(R.string.CallLinkInfoSheet__remove) { _, _ ->
        AppDependencies.signalCallManager.removeFromCallLink(callParticipant)
      }
      .setNeutralButton(R.string.CallLinkInfoSheet__block_from_call) { _, _ ->
        AppDependencies.signalCallManager.blockFromCallLink(callParticipant)
      }
      .show()
  }
}
