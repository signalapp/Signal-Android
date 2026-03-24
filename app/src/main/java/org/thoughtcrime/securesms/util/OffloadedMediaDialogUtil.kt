package org.thoughtcrime.securesms.util

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity

object OffloadedMediaDialogUtil {

  /**
   * Show dialog when all selected media has been offloaded. No save is attempted.
   */
  @JvmStatic
  fun showAllOffloaded(context: Context) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.AttachmentSaver__cant_save_media)
      .setMessage(R.string.AttachmentSaver__media_offloaded_message)
      .setCancelable(true)
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(R.string.AttachmentSaver__view_storage_settings) { _, _ -> context.startActivity(AppSettingsActivity.manageStorage(context)) }
      .show()
  }

  /**
   * Show dialog when some selected media has been offloaded. OK proceeds with saving the non-offloaded items.
   */
  @JvmStatic
  fun showPartiallyOffloaded(context: Context, onProceed: Runnable) {
    MaterialAlertDialogBuilder(context)
      .setTitle(R.string.AttachmentSaver__cant_save_all_items)
      .setMessage(R.string.AttachmentSaver__some_media_offloaded_message)
      .setCancelable(true)
      .setPositiveButton(android.R.string.ok) { _, _ -> onProceed.run() }
      .setNegativeButton(R.string.AttachmentSaver__view_storage_settings) { _, _ -> context.startActivity(AppSettingsActivity.manageStorage(context)) }
      .show()
  }
}
