package org.thoughtcrime.securesms.components

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import org.thoughtcrime.securesms.R

/**
 * Fullscreen Dialog Fragment which will dismiss itself when the keyboard is closed
 */
abstract class KeyboardEntryDialogFragment(@LayoutRes contentLayoutId: Int) :
  DialogFragment(contentLayoutId),
  KeyboardAwareLinearLayout.OnKeyboardShownListener,
  KeyboardAwareLinearLayout.OnKeyboardHiddenListener {

  private var hasShown = false

  protected open val withDim: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    setStyle(STYLE_NORMAL, R.style.Theme_Signal_RoundedBottomSheet)
    super.onCreate(savedInstanceState)
  }

  @Suppress("DEPRECATION")
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState)

    if (!withDim) {
      dialog.window?.setDimAmount(0f)
    }

    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    return dialog
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    hasShown = false

    val view = super.onCreateView(inflater, container, savedInstanceState)
    return if (view is KeyboardAwareLinearLayout) {
      view.addOnKeyboardShownListener(this)
      view.addOnKeyboardHiddenListener(this)
      view
    } else {
      throw IllegalStateException("Expected parent of view hierarchy to be keyboard aware.")
    }
  }

  override fun onKeyboardShown() {
    hasShown = true
  }

  override fun onKeyboardHidden() {
    if (hasShown) {
      dismissAllowingStateLoss()
    }
  }
}
