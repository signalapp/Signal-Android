/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import androidx.annotation.ColorInt
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.signal.core.ui.util.ThemeUtil
import org.signal.core.util.DimensionUnit
import com.google.android.material.R as MaterialR

/**
 * Forces rounded corners on BottomSheet.
 *
 * Expects [R.attr.fixedRoundedCornerBottomSheetStyle] to be defined in your app theme, pointing to a
 * style that extends [Widget.CoreUi.FixedRoundedCorners]. Subclasses can override [themeResId] to
 * use an alternate style.
 */
abstract class FixedRoundedCornerBottomSheetDialogFragment : BottomSheetDialogFragment() {

  /**
   * Sheet corner radius in DP
   */
  protected open val cornerRadius: Int
    get() = if (resources.getWindowSizeClass().isSplitPane()) {
      32
    } else {
      18
    }

  protected open val peekHeightPercentage: Float = 0.5f

  protected open val themeResId: Int
    get() = ThemeUtil.getThemedResourceId(requireContext(), R.attr.fixedRoundedCornerBottomSheetStyle)

  @ColorInt
  protected var backgroundColor: Int = Color.TRANSPARENT

  private lateinit var dialogBackground: MaterialShapeDrawable

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NORMAL, themeResId)
  }

  override fun onResume() {
    super.onResume()
    dialog?.window?.initializeScreenshotSecurity()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

    dialog.behavior.peekHeight = (resources.displayMetrics.heightPixels * peekHeightPercentage).toInt()

    val cornerRadiusPx = DimensionUnit.DP.toPixels(cornerRadius.toFloat())
    val shapeAppearanceModel = ShapeAppearanceModel.builder()
      .setTopLeftCorner(CornerFamily.ROUNDED, cornerRadiusPx)
      .setTopRightCorner(CornerFamily.ROUNDED, cornerRadiusPx)
      .build()

    dialogBackground = MaterialShapeDrawable(shapeAppearanceModel)

    val bottomSheetStyle = ThemeUtil.getThemedResourceId(ContextThemeWrapper(requireContext(), themeResId), MaterialR.attr.bottomSheetStyle)
    backgroundColor = ThemeUtil.getThemedColor(ContextThemeWrapper(requireContext(), bottomSheetStyle), MaterialR.attr.backgroundTint)
    dialogBackground.fillColor = ColorStateList.valueOf(backgroundColor)

    dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        if (bottomSheet.background !== dialogBackground) {
          bottomSheet.background = dialogBackground
        }
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    })

    return dialog
  }
}
