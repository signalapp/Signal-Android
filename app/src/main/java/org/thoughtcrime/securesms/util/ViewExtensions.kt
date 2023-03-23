package org.thoughtcrime.securesms.util

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

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
