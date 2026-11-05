/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util.views

import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import org.signal.core.ui.view.Stub
import org.thoughtcrime.securesms.util.CachedInflater

/**
 * Resolves a [ViewStub] the way [ViewStub.inflate] does, but takes the view from [CachedInflater] when one has been
 * inflated ahead of time, keeping the inflation off the bind path.
 */
object CachedStubInflater : Stub.Inflater {

  override fun inflate(viewStub: ViewStub): View {
    val parent = viewStub.parent as? ViewGroup ?: return viewStub.inflate()
    val layoutRes = viewStub.layoutResource
    if (layoutRes == 0) {
      return viewStub.inflate()
    }

    val view: View = CachedInflater.from(viewStub.context).inflate(layoutRes, parent, false)
    if (viewStub.inflatedId != View.NO_ID) {
      view.id = viewStub.inflatedId
    }

    val index = parent.indexOfChild(viewStub)
    parent.removeViewInLayout(viewStub)

    val layoutParams = viewStub.layoutParams
    if (layoutParams != null) {
      parent.addView(view, index, layoutParams)
    } else {
      parent.addView(view, index)
    }

    return view
  }
}
