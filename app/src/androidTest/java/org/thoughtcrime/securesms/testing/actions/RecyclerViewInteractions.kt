/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testing.actions

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
 *
 * The same loop also waits on [isClearOfSystemBars], since a scrolled-to target still has to be clickable.
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
    val scrolled = tryScrollTo(recyclerViewId, target) || tryScrollTo(recyclerViewId, hasDescendant(target))
    if (scrolled && isClearOfSystemBars(target)) {
      return
    }

    if (SystemClock.uptimeMillis() >= deadline) {
      val reason = if (scrolled) "left $target under the system bars" else "never bound a holder matching $target"
      throw AssertionError("RecyclerView (id=$recyclerViewId) $reason within ${timeoutMs}ms.")
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

/**
 * Whether the point [androidx.test.espresso.action.GeneralLocation.VISIBLE_CENTER] would click for [target] lands
 * inside the app's window rather than on a system bar. The hosts are edge-to-edge and pad their list with the
 * navigation bar inset asynchronously ([org.thoughtcrime.securesms.util.SystemWindowInsetsSetter]), so until that
 * lands the bottom-most row sits behind the bar — still "visible" to Espresso, which then computes a click point
 * in SystemUI's window and has the injection rejected with an `InjectEventSecurityException`.
 *
 * Unresolvable targets (no match, several, or no visible bounds at all) return true, since only a click can
 * report those accurately.
 */
private fun isClearOfSystemBars(target: Matcher<View>): Boolean {
  var clear = true

  runCatching {
    onView(target).check { view, noMatch ->
      if (view == null || noMatch != null) {
        return@check
      }

      val visibleBounds = Rect()
      if (!view.getGlobalVisibleRect(visibleBounds)) {
        return@check
      }

      val insets = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.systemBars()) ?: return@check
      val root = view.rootView

      clear = visibleBounds.centerY() in insets.top until (root.height - insets.bottom) &&
        visibleBounds.centerX() in insets.left until (root.width - insets.right)
    }
  }

  return clear
}
