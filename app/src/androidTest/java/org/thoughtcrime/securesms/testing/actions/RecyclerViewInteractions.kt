/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.actions

import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.rxjava3.schedulers.TestScheduler
import org.hamcrest.Matcher

/**
 * Scrolls the [RecyclerView] with id [recyclerViewId] to the view holder whose item view matches [target], or
 * whose item view contains a descendant matching [target] (e.g. a preset button inside a container row),
 * binding it if necessary, then returns. Off-screen presets/buttons are brought on-screen before a click or
 * assertion regardless of device size — important for Firebase Test Lab's varied screens.
 *
 * The DSL screens use [androidx.recyclerview.widget.ListAdapter], which diffs `submitList` on a background
 * thread and posts the result to the main thread. espresso-contrib's [RecyclerViewActions.scrollTo] scans the
 * adapter once and fails fast if that diff has not yet committed, so we retry within [timeoutMs], pumping the
 * main looper (and, if supplied, advancing [scheduler] to run the Rx work that produces the list) between
 * attempts. This is the async-diff analogue of the codebase's existing poll-until-ready test idiom; Android
 * exposes no deterministic completion hook for the differ.
 */
fun scrollToDescendant(
  @IdRes recyclerViewId: Int,
  target: Matcher<View>,
  scheduler: TestScheduler? = null,
  timeoutMs: Long = 5_000
) {
  val deadline = SystemClock.uptimeMillis() + timeoutMs

  while (true) {
    // A holder's item view may itself be the target (a bare button row) or contain it as a descendant
    // (a preset within a container), so try both rather than assume one shape.
    if (tryScrollTo(recyclerViewId, target) || tryScrollTo(recyclerViewId, hasDescendant(target))) {
      return
    }

    if (SystemClock.uptimeMillis() >= deadline) {
      throw AssertionError("RecyclerView (id=$recyclerViewId) never bound a holder matching $target within ${timeoutMs}ms.")
    }

    scheduler?.triggerActions()
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    Thread.sleep(50)
  }
}

private fun tryScrollTo(@IdRes recyclerViewId: Int, holderMatcher: Matcher<View>): Boolean {
  return try {
    onView(withId(recyclerViewId)).perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(holderMatcher))
    true
  } catch (e: PerformException) {
    false
  }
}
