package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
class BackupFrequencyPickerDialogFragment(private val defaultFrequency: BackupFrequencyV1) : DialogFragment() {

  private val frequencyOptions = BackupFrequencyV1.entries
  private var selectedFrequency: BackupFrequencyV1 = defaultFrequency
  private var callback: OnClickListener? = null

  override fun onCreateDialog(savedInstance: Bundle?): Dialog {
    val context = requireContext()
    val localizedFrequencyOptions = frequencyOptions
      .map { it.getResourceId() }
      .map { context.getString(it) }
      .toTypedArray()
    val defaultIndex = frequencyOptions.indexOf(defaultFrequency)

    return MaterialAlertDialogBuilder(context)
      .setSingleChoiceItems(localizedFrequencyOptions, defaultIndex) { _, selectedIndex ->
        selectedFrequency = frequencyOptions[selectedIndex]
      }
      .setTitle(R.string.BackupFrequencyPickerDialogFragment__set_backup_frequency)
      .setPositiveButton(R.string.BackupFrequencyPickerDialogFragment__ok, callback)
      .setNegativeButton(R.string.BackupFrequencyPickerDialogFragment__cancel, null)
      .create()
  }

  fun getValue(): BackupFrequencyV1 = selectedFrequency

  fun setOnPositiveButtonClickListener(cb: OnClickListener) {
    this.callback = cb
  }
}
