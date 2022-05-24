package org.thoughtcrime.securesms.util

import android.view.View
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
