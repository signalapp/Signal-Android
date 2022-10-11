package org.thoughtcrime.securesms.components

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Manages the lifecycle of displaying a dialog fragment. Will automatically close and nullify the reference
 * if the bound lifecycle is destroyed, and handles repeat calls to show such that no more than one dialog is
 * displayed.
 */
class DialogFragmentDisplayManager(private val builder: () -> DialogFragment) : DefaultLifecycleObserver {

  private var dialogFragment: DialogFragment? = null

  fun show(lifecycleOwner: LifecycleOwner, fragmentManager: FragmentManager, tag: String? = null) {
    val fragment = dialogFragment ?: builder()
    if (fragment.dialog?.isShowing != true) {
      fragment.show(fragmentManager, tag)
      dialogFragment = fragment
      lifecycleOwner.lifecycle.addObserver(this)
    }
  }

  fun hide() {
    dialogFragment?.dismissAllowingStateLoss()
    dialogFragment = null
  }

  override fun onDestroy(owner: LifecycleOwner) {
    owner.lifecycle.removeObserver(this)
    hide()
  }
}
