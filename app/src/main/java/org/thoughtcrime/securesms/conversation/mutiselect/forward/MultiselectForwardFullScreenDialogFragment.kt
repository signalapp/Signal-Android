package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FullScreenDialogFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment.Companion.DIALOG_TITLE
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.fragments.findListener

class MultiselectForwardFullScreenDialogFragment : FullScreenDialogFragment(), MultiselectForwardFragment.Callback {
  override fun getTitle(): Int = requireArguments().getInt(DIALOG_TITLE)

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

  override fun getDialogBackgroundColor(): Int {
    return ContextCompat.getColor(requireContext(), R.color.signal_background_primary)
  }

  override fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? {
    return findListener<Callback>()?.getStorySendRequirements()
  }

  override fun getContainer(): ViewGroup {
    return requireView().findViewById(R.id.full_screen_dialog_content) as ViewGroup
  }

  override fun setResult(bundle: Bundle) {
    setFragmentResult(MultiselectForwardFragment.RESULT_KEY, bundle)
  }

  override fun exitFlow() {
    dismissAllowingStateLoss()
  }

  override fun onSearchInputFocused() = Unit

  interface Callback {
    fun onFinishForwardAction() = Unit
    fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? = null
  }
}
