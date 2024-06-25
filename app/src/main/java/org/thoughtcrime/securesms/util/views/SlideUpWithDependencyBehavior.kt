package org.thoughtcrime.securesms.util.views

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Px
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlin.math.min

/**
 * @param offsetY - Extra padding between the dependency and child.
 * @param maxTranslationY - The maximum offset to apply to child's translationY value. This should be a negative number.
 */
abstract class SlideUpWithDependencyBehavior(
  context: Context,
  attributeSet: AttributeSet?,
  @field:Px @param:Px private val offsetY: Float = 0f
) : CoordinatorLayout.Behavior<View>(context, attributeSet) {

  private val rect = Rect()

  override fun onDependentViewChanged(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
    dependency.getLocalVisibleRect(rect)

    val height = if (rect.top < parent.bottom) {
      rect.height()
    } else {
      0
    }

    val translationY = min(0.0, (dependency.translationY - (height + offsetY)).toDouble()).toFloat()
    child.translationY = translationY

    return true
  }

  override fun onDependentViewRemoved(parent: CoordinatorLayout, child: View, dependency: View) {
    child.translationY = 0f
  }

  abstract override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean
}
