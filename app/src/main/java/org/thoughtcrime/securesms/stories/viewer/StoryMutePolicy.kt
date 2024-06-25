package org.thoughtcrime.securesms.stories.viewer

import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.util.AppForegroundObserver

/**
 * Stories are to start muted, and once unmuted, remain as such until the
 * user backgrounds the application.
 */
object StoryMutePolicy : AppForegroundObserver.Listener {
  var isContentMuted: Boolean = true

  fun initialize() {
    AppDependencies.appForegroundObserver.addListener(this)
  }

  override fun onBackground() {
    isContentMuted = true
  }
}
