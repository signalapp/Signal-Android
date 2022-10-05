package org.thoughtcrime.securesms.components

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.R

/**
 * Displays a small progress spinner in a card view, as a non-cancellable dialog fragment.
 */
class ProgressCardDialogFragment : DialogFragment(R.layout.progress_card_dialog) {
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    isCancelable = false
    return super.onCreateDialog(savedInstanceState).apply {
      this.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
  }
}
