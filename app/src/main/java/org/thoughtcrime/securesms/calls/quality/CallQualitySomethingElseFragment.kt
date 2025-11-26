/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.quality

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import org.thoughtcrime.securesms.compose.ComposeFullScreenDialogFragment

/**
 * Fragment which allows user to enter additional text to describe a call issue.
 */
class CallQualitySomethingElseFragment : ComposeFullScreenDialogFragment() {

  companion object {
    const val REQUEST_KEY = "CallQualitySomethingElseRequestKey"

    fun create(somethingElseDescription: String): DialogFragment {
      return CallQualitySomethingElseFragment().apply {
        arguments = bundleOf(REQUEST_KEY to somethingElseDescription)
      }
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)

    dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    return dialog
  }

  @Composable
  override fun DialogContent() {
    val initialState = remember { requireArguments().getString(REQUEST_KEY) ?: "" }

    CallQualitySomethingElseScreen(
      somethingElseDescription = initialState,
      onSaveClick = {
        dismissAllowingStateLoss()
        setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to it))
      },
      onCancelClick = {
        dismissAllowingStateLoss()
      }
    )
  }
}
