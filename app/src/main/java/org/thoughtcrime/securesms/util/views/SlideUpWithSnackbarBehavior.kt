package org.thoughtcrime.securesms.util.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.Snackbar.SnackbarLayout
import org.signal.core.util.dp

class SlideUpWithSnackbarBehavior(context: Context, attributeSet: AttributeSet?) : SlideUpWithDependencyBehavior(context, attributeSet, 16f.dp) {
  @SuppressLint("RestrictedApi")
  override fun layoutDependsOn(
    parent: CoordinatorLayout,
    child: View,
    dependency: View
  ): Boolean {
    return dependency is SnackbarLayout
  }
}
