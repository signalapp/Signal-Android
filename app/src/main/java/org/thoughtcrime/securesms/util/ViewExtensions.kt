package org.thoughtcrime.securesms.util

import android.view.View

var View.visible: Boolean
  get() {
    return this.visibility == View.VISIBLE
  }

  set(value) {
    this.visibility = if (value) View.VISIBLE else View.GONE
  }
