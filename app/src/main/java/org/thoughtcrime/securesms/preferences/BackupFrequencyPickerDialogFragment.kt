package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import org.thoughtcrime.securesms.R

class BackupFrequencyPickerDialogFragment(private val defaultFrequency: Int) : DialogFragment() {
  private val dayOptions = arrayOf("1", "7", "30", "90", "180", "365")
  private var index: Int = 0
  private var callback: OnClickListener? = null

  override fun onCreateDialog(savedInstance: Bundle?): Dialog {
    val defaultIndex = this.dayOptions.indexOf(this.defaultFrequency.toString())  // preselect the backup frequency choice if it's valid
    this.index = defaultIndex
    return MaterialAlertDialogBuilder(requireContext())
      .setSingleChoiceItems(this.dayOptions, defaultIndex) { _, i -> this.index = i }
      .setTitle(R.string.BackupFrequencyPickerDialogFragment__enter_frequency)
      .setPositiveButton(R.string.BackupFrequencyPickerDialogFragment__ok, this.callback)
      .setNegativeButton(R.string.BackupFrequencyPickerDialogFragment__cancel, null)
      .create()
  }

  fun getValue(): Int = this.dayOptions[this.index].toInt()

  fun setOnPositiveButtonClickListener(cb: OnClickListener) {
    this.callback = cb
  }
}
