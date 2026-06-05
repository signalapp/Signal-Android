/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaoverview

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.core.Completable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentSaver
import org.thoughtcrime.securesms.database.MediaTable

internal object MediaActions {

  @JvmStatic
  fun handleSaveMedia(fragment: Fragment, mediaRecords: Collection<MediaTable.MediaRecord>): Completable {
    return AttachmentSaver(fragment).saveAttachmentsRx(mediaRecords)
  }

  @JvmStatic
  fun handleDeleteMedia(fragment: Fragment, mediaRecords: Collection<MediaTable.MediaRecord>) {
    val recordCount = mediaRecords.size
    val res = fragment.resources
    val confirmTitle = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_title, recordCount, recordCount)
    val confirmMessage = res.getQuantityString(R.plurals.MediaOverviewActivity_Media_delete_confirm_message, recordCount, recordCount)

    MaterialAlertDialogBuilder(fragment.requireContext())
      .setTitle(confirmTitle)
      .setMessage(confirmMessage)
      .setCancelable(true)
      .setPositiveButton(R.string.delete) { _, _ ->
        val viewModel = ViewModelProvider(fragment)[MediaDeleteProgressViewModel::class.java]
        viewModel.start(mediaRecords)
        MediaDeleteProgressDialogFragment.show(fragment)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
