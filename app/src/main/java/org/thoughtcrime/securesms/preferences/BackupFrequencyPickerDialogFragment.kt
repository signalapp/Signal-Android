package org.thoughtcrime.securesms.preferences

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment

class BackupFrequencyPickerDialogFragment(private val defaultFrequency: Int) : DialogFragment() {
  private val dayOptions = arrayOf("1", "7", "30", "90", "180", "365")
  private var index: Int = 0
  private var callback: OnClickListener? = null

  override fun onCreateDialog(savedInstance: Bundle?): Dialog {
    val defaultIndex = this.dayOptions.indexOf(this.defaultFrequency.toString())  // preselect the backup frequency choice if it's valid
    return AlertDialog.Builder(requireContext())
      .setSingleChoiceItems(this.dayOptions, defaultIndex) { _, i -> this.index = i }
      .setTitle("Every N days")
      .setPositiveButton("OK") { dialog, i ->
          val backupFrequencyDays = this.dayOptions[this.index].toInt()
          Toast.makeText(requireContext(), "Backup every $backupFrequencyDays days", Toast.LENGTH_LONG).show()
          callback?.onClick(dialog, i)
      }
      .setNegativeButton("Cancel", null)
      .create()
  }

  fun getValue(): Int = this.dayOptions[this.index].toInt()

  fun setOnPositiveButtonClickListener(cb: OnClickListener) {
    this.callback = cb
  }
}