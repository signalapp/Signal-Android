package org.thoughtcrime.securesms.util

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnNextLayout

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
