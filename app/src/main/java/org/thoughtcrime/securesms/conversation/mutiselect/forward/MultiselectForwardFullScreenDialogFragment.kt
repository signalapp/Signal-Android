package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.fragments.findListener

class MultiselectForwardFullScreenDialogFragment : DialogFragment(), MultiselectForwardFragment.Callback {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  override fun onFinishForwardAction() {
    findListener<Callback>()?.onFinishForwardAction()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.multiselect_forward_activity, container, false)
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
    return requireView().findViewById(R.id.fragment_container_wrapper)!!
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
