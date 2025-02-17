/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.actions

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher

/**
 * Scrolls the RecyclerView to the bottom position.
 *
 * Borrowed from [https://stackoverflow.com/a/55990445](https://stackoverflow.com/a/55990445)
 */
object RecyclerViewScrollToBottomAction : ViewAction {
  override fun getDescription(): String = "scroll RecyclerView to bottom"

  override fun getConstraints(): Matcher<View> = allOf(isAssignableFrom(RecyclerView::class.java), isDisplayed())

  override fun perform(uiController: UiController?, view: View?) {
    val recyclerView = view as RecyclerView
    val itemCount = recyclerView.adapter?.itemCount
    val position = itemCount?.minus(1) ?: 0
    recyclerView.scrollToPosition(position)
    uiController?.loopMainThreadUntilIdle()
  }
}
