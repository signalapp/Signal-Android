@file:Suppress("DEPRECATION")

package org.thoughtcrime.securesms.components

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface

/**
 * Wraps a normal progress dialog for showing blocking in-progress UI.
 */
class SignalProgressDialog private constructor(val progressDialog: ProgressDialog) {

  val isShowing: Boolean
    get() = progressDialog.isShowing

  fun hide() {
    progressDialog.hide()
  }

  fun dismiss() {
    progressDialog.dismiss()
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
      return SignalProgressDialog(ProgressDialog.show(context, title, message, indeterminate, cancelable, cancelListener))
    }
  }
}
