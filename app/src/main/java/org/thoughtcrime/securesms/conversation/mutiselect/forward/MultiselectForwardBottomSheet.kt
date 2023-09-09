package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.Stories
import org.thoughtcrime.securesms.util.fragments.findListener

class MultiselectForwardBottomSheet : FixedRoundedCornerBottomSheetDialogFragment(), MultiselectForwardFragment.Callback {

  override val peekHeightPercentage: Float = 0.67f

  private var callback: Callback? = null

  companion object {
    private val TAG = Log.tag(MultiselectForwardBottomSheet::class.java)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.multiselect_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    Log.d(TAG, "onViewCreated()")

    callback = findListener<Callback>()

    if (savedInstanceState == null) {
      val fragment = MultiselectForwardFragment()
      fragment.arguments = requireArguments()

      childFragmentManager.beginTransaction()
        .replace(R.id.multiselect_container, fragment)
        .commitAllowingStateLoss()
    }
  }

  override fun getContainer(): ViewGroup {
    return requireView().parent.parent.parent as ViewGroup
  }

  override fun getDialogBackgroundColor(): Int {
    return backgroundColor
  }

  override fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? {
    return findListener<Callback>()?.getStorySendRequirements()
  }
  override fun setResult(bundle: Bundle) {
    setFragmentResult(MultiselectForwardFragment.RESULT_KEY, bundle)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    Log.d(TAG, "onDismiss()")
    callback?.onDismissForwardSheet()
  }

  override fun onFinishForwardAction() {
    Log.d(TAG, "onFinishForwardAction()")
    callback?.onFinishForwardAction()
  }

  override fun exitFlow() {
    Log.d(TAG, "exitFlow()")
    dismissAllowingStateLoss()
  }

  override fun onSearchInputFocused() {
    Log.d(TAG, "onSearchInputFocused()")
    (requireDialog() as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  interface Callback {
    fun onFinishForwardAction()
    fun onDismissForwardSheet()
    fun getStorySendRequirements(): Stories.MediaTransform.SendRequirements? = null
  }
}
