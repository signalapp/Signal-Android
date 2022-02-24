package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FullScreenDialogFragment
import org.thoughtcrime.securesms.util.fragments.findListener

class MultiselectForwardFullScreenDialogFragment : FullScreenDialogFragment(), MultiselectForwardFragment.Callback {
  override fun getTitle(): Int = R.string.MediaReviewFragment__send_to

  override fun getDialogLayoutResource(): Int = R.layout.fragment_container

  override fun onFinishForwardAction() {
    findListener<Callback>()?.onFinishForwardAction()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    if (savedInstanceState == null) {
      val fragment = MultiselectForwardFragment()
      fragment.arguments = requireArguments()

      childFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, fragment)
        .commitAllowingStateLoss()
    }
  }

  override fun getContainer(): ViewGroup {
    return requireView().findViewById(R.id.full_screen_dialog_content) as ViewGroup
  }

  override fun setResult(bundle: Bundle) {
    setFragmentResult(MultiselectForwardFragment.RESULT_SELECTION, bundle)
  }

  override fun exitFlow() {
    dismissAllowingStateLoss()
  }

  override fun onSearchInputFocused() = Unit

  interface Callback {
    fun onFinishForwardAction()
  }
}
