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
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible

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
    set(value) = if (Build.VERSION.SDK_INT >= 24) {
      progressBar.setProgress(value, true)
    } else {
      progressBar.setProgress(value)
    }

  fun setMessage(message: CharSequence?) {
    messageView.text = message
  }

  fun hide() {
    if (dialog.window?.decorView?.isAttachedToWindow == true) {
      dialog.hide()
    }
  }

  fun dismiss() {
    if (dialog.window?.decorView?.isAttachedToWindow == true) {
      dialog.dismiss()
    }
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
      cancelListener: DialogInterface.OnCancelListener? = null,
      negativeButtonText: CharSequence? = null,
      negativeButtonListener: DialogInterface.OnClickListener? = null
    ): SignalProgressDialog {
      val builder = MaterialAlertDialogBuilder(context).apply {
        setTitle(null)
        setMessage(null)
        setCancelable(cancelable)
        setOnCancelListener(cancelListener)
        if (negativeButtonText != null) {
          setNegativeButton(negativeButtonText, negativeButtonListener)
        }
      }

      val customView = LayoutInflater.from(context).inflate(R.layout.signal_progress_dialog, null) as ConstraintLayout
      val titleView: TextView = customView.findViewById(R.id.progress_dialog_title)
      val messageView: TextView = customView.findViewById(R.id.progress_dialog_message)
      val progressView: CircularProgressIndicator = customView.findViewById(R.id.progress_dialog_progressbar)

      titleView.text = title
      titleView.visible = title != null
      messageView.text = message
      messageView.visible = message != null
      progressView.isIndeterminate = indeterminate

      if (title == null && message == null) {
        progressView.padding(top = 32.dp, bottom = 32.dp)
      }

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
