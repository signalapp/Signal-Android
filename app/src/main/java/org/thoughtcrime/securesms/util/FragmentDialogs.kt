package org.thoughtcrime.securesms.util

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * Helper functions to display custom views in AlertDialogs anchored to the top of the specified view.
 */
object FragmentDialogs {

  fun Fragment.displayInDialogAboveAnchor(
    anchorView: View,
    @LayoutRes contentLayoutId: Int,
    windowDim: Float = -1f,
    onShow: (DialogInterface, View) -> Unit = { _, _ -> }
  ): DialogInterface {
    val contentView = LayoutInflater.from(anchorView.context).inflate(contentLayoutId, requireView() as ViewGroup, false)

    contentView.measure(
      View.MeasureSpec.makeMeasureSpec(contentView.layoutParams.width, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(contentView.layoutParams.height, View.MeasureSpec.EXACTLY)
    )

    contentView.layout(0, 0, contentView.measuredWidth, contentView.measuredHeight)

    return displayInDialogAboveAnchor(anchorView, contentView, windowDim, onShow)
  }

  fun Fragment.displayInDialogAboveAnchor(
    anchorView: View,
    contentView: View,
    windowDim: Float = -1f,
    onShow: (DialogInterface, View) -> Unit = { _, _ -> },
    onDismiss: (DialogInterface) -> Unit = { }
  ): DialogInterface {
    val alertDialog = AlertDialog.Builder(requireContext())
      .setView(contentView)
      .create()

    alertDialog.window!!.attributes = alertDialog.window!!.attributes.apply {
      val viewProjection = Projection.relativeToViewRoot(anchorView, null).translateY(anchorView.translationY)
      this.y = (viewProjection.y - contentView.height).toInt()
      this.gravity = Gravity.TOP

      viewProjection.release()
    }

    if (windowDim >= 0f) {
      alertDialog.window!!.setDimAmount(windowDim)
    }

    alertDialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    alertDialog.setOnDismissListener(onDismiss)

    alertDialog.setOnShowListener { onShow(alertDialog, contentView) }

    alertDialog.show()

    return alertDialog
  }
}
