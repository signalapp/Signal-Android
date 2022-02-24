package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.fragments.findListener

class MultiselectForwardBottomSheet : FixedRoundedCornerBottomSheetDialogFragment(), MultiselectForwardFragment.Callback {

  override val peekHeightPercentage: Float = 0.67f

  private var callback: Callback? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.multiselect_bottom_sheet, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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

  override fun setResult(bundle: Bundle) {
    setFragmentResult(MultiselectForwardFragment.RESULT_SELECTION, bundle)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    callback?.onDismissForwardSheet()
  }

  override fun onFinishForwardAction() {
    callback?.onFinishForwardAction()
  }

  override fun exitFlow() {
    dismissAllowingStateLoss()
  }

  override fun onSearchInputFocused() {
    (requireDialog() as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  interface Callback {
    fun onFinishForwardAction()
    fun onDismissForwardSheet()
  }
}
