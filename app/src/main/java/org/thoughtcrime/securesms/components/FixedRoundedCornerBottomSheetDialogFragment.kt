package org.thoughtcrime.securesms.components

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Forces rounded corners on BottomSheet
 */
abstract class FixedRoundedCornerBottomSheetDialogFragment : BottomSheetDialogFragment() {

  protected open val peekHeightPercentage: Float = 0.5f

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, R.style.Widget_Signal_FixedRoundedCorners)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

    dialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * peekHeightPercentage).toInt()

    val shapeAppearanceModel = ShapeAppearanceModel.builder()
      .setTopLeftCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), 18).toFloat())
      .setTopRightCorner(CornerFamily.ROUNDED, ViewUtil.dpToPx(requireContext(), 18).toFloat())
      .build()

    val dialogBackground = MaterialShapeDrawable(shapeAppearanceModel)

    dialogBackground.setTint(ContextCompat.getColor(requireContext(), R.color.signal_background_dialog))

    dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        if (bottomSheet.background !== dialogBackground) {
          ViewCompat.setBackground(bottomSheet, dialogBackground)
        }
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    })

    return dialog
  }
}
