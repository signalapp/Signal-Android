@file:Suppress("DEPRECATION")

package org.thoughtcrime.securesms.components

import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Wraps a normal progress dialog for showing blocking in-progress UI.
 */
class SignalProgressDialog private constructor(
  private val dialog: AlertDialog,
  private val titleView: TextView,
  private val messageView: TextView,
  private val progressBar: CircularProgressIndicator
) {

  val isShowing: Boolean
    get() = dialog.isShowing

  var isIndeterminate: Boolean
    get() = progressBar.isIndeterminate
    set(value) = progressBar.setIndeterminate(value)

  var progress: Int
    get() = progressBar.progress
    set(value) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      progressBar.setProgress(value, true)
    } else {
      progressBar.setProgress(value)
    }

  fun setMessage(message: CharSequence?) {
    messageView.text = message
  }

  fun hide() {
    dialog.hide()
  }

  fun dismiss() {
    dialog.dismiss()
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun show(
      context: Context,
      title: CharSequence? = null,
      message: CharSequence? = null,
      indeterminate: Boolean = false,
      cancelable: Boolean = false,
      cancelListener: DialogInterface.OnCancelListener? = null
    ): SignalProgressDialog {
      val builder = MaterialAlertDialogBuilder(context).apply {
        setTitle(null)
        setMessage(null)
        setCancelable(cancelable)
        setOnCancelListener(cancelListener)
      }

      val customView = LayoutInflater.from(context).inflate(R.layout.signal_progress_dialog, null) as ConstraintLayout
      val titleView: TextView = customView.findViewById(R.id.progress_dialog_title)
      val messageView: TextView = customView.findViewById(R.id.progress_dialog_message)
      val progressView: CircularProgressIndicator = customView.findViewById(R.id.progress_dialog_progressbar)

      titleView.text = title
      messageView.text = message
      progressView.isIndeterminate = indeterminate

      builder.setView(customView)
      val dialog = builder.show()

      val layoutParams = WindowManager.LayoutParams()
      layoutParams.copyFrom(dialog.window?.attributes)
      layoutParams.width = ViewUtil.dpToPx(context, 260)
      dialog.window?.attributes = layoutParams

      return SignalProgressDialog(dialog, titleView, messageView, progressView)
    }
  }
}
