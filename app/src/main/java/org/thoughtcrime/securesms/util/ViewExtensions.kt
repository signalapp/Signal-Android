package org.thoughtcrime.securesms.util

import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment
import androidx.lifecycle.Lifecycle

var View.visible: Boolean
  get() {
    return this.visibility == View.VISIBLE
  }
  set(value) {
    this.visibility = if (value) View.VISIBLE else View.GONE
  }

fun View.padding(left: Int = paddingLeft, top: Int = paddingTop, right: Int = paddingRight, bottom: Int = paddingBottom) {
  setPadding(left, top, right, bottom)
}

fun ConstraintLayout.changeConstraints(change: ConstraintSet.() -> Unit) {
  val set = ConstraintSet()
  set.clone(this)
  set.change()
  set.applyTo(this)
}

inline fun View.doOnEachLayout(crossinline action: (view: View) -> Unit): View.OnLayoutChangeListener {
  val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> action(view) }
  addOnLayoutChangeListener(listener)
  return listener
}

/**
 * OnLayout gets called prior to the view *actually* being laid out. This
 * method will wait until the next layout and then post the action to happen
 * afterwards.
 */
inline fun View.doAfterNextLayout(crossinline action: () -> Unit) {
  doOnNextLayout {
    post { action() }
  }
}

fun TextView.setRelativeDrawables(
  @DrawableRes start: Int = 0,
  @DrawableRes top: Int = 0,
  @DrawableRes bottom: Int = 0,
  @DrawableRes end: Int = 0
) {
  setCompoundDrawablesRelativeWithIntrinsicBounds(
    start,
    top,
    end,
    bottom
  )
}

/**
 * Get a lifecycle associated with this view. Care must be taken to ensure
 * if activity fallback occurs that the context of the view is correct.
 */
fun View.getLifecycle(): Lifecycle? {
  return try {
    findFragment<Fragment>().viewLifecycleOwner.lifecycle
  } catch (e: IllegalStateException) {
    ViewUtil.getActivityLifecycle(this)
  }
}

fun View.layoutIn(parent: View) {
  val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
  val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)
  val childWidth = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, layoutParams.width)
  val childHeight = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, layoutParams.height)
  measure(childWidth, childHeight)
  layout(0, 0, measuredWidth, measuredHeight)
}

fun View.drawAsTopItemDecoration(canvas: Canvas, parent: View, child: View, offset: Int = 0) {
  canvas.save()
  val left = parent.left
  val top = child.y.toInt() - height - offset
  canvas.translate(left.toFloat(), top.toFloat())
  draw(canvas)
  canvas.restore()
}
