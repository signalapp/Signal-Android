/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.WebRtcCallActivity
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * CallIntent wraps an intent inside one of the call activities to allow for easy typed access to the necessary data within it.
 */
class CallIntent(
  private val intent: Intent
) {

  companion object {

    private const val CALL_INTENT_PREFIX = "CallIntent"

    private fun getActivityClass(): Class<out Activity> = if (RemoteConfig.newCallUi || SignalStore.internal.newCallingUi) {
      CallActivity::class.java
    } else {
      WebRtcCallActivity::class.java
    }

    private fun getActionString(action: Action): String {
      return "$CALL_INTENT_PREFIX.${action.code}"
    }

    private fun getExtraString(extra: Extra): String {
      return "$CALL_INTENT_PREFIX.${extra.code}"
    }
  }

  val action: Action by lazy { Action.fromIntent(intent) }

  @get:JvmName("shouldEnableVideoIfAvailable")
  var shouldEnableVideoIfAvailable: Boolean
    get() = intent.getBooleanExtra(getExtraString(Extra.ENABLE_VIDEO_IF_AVAILABLE), false)
    set(value) {
      intent.putExtra(getExtraString(Extra.ENABLE_VIDEO_IF_AVAILABLE), value)
    }

  val isStartedFromFullScreen: Boolean by lazy { intent.getBooleanExtra(getExtraString(Extra.STARTED_FROM_FULLSCREEN), false) }

  val isStartedFromCallLink: Boolean by lazy { intent.getBooleanExtra(getExtraString(Extra.STARTED_FROM_CALL_LINK), false) }

  @get:JvmName("shouldLaunchInPip")
  val shouldLaunchInPip: Boolean by lazy { intent.getBooleanExtra(getExtraString(Extra.LAUNCH_IN_PIP), false) }

  override fun toString(): String {
    return """
      CallIntent
      Action - $action
      Enable video if available? $shouldEnableVideoIfAvailable
      Started from full screen? $isStartedFromFullScreen
      Started from call link? $isStartedFromCallLink
      Launch in pip? $shouldLaunchInPip
    """.trimIndent()
  }

  enum class Action(val code: String) {
    VIEW(Intent.ACTION_VIEW),
    ANSWER_AUDIO("ANSWER_ACTION"),
    ANSWER_VIDEO("ANSWER_VIDEO_ACTION"),
    DENY("DENY_ACTION"),
    END_CALL("END_CALL_ACTION");

    companion object {
      fun fromIntent(intent: Intent): Action {
        return intent.action?.let { a -> entries.firstOrNull { a == it.code || a == getActionString(it) } } ?: VIEW
      }
    }
  }

  private enum class Extra(val code: String) {
    ENABLE_VIDEO_IF_AVAILABLE("ENABLE_VIDEO_IF_AVAILABLE"),
    STARTED_FROM_FULLSCREEN("STARTED_FROM_FULLSCREEN"),
    STARTED_FROM_CALL_LINK("STARTED_FROM_CALL_LINK"),
    LAUNCH_IN_PIP("LAUNCH_IN_PIP")
  }

  /**
   * Builds an intent to launch the call screen.
   */
  class Builder(val context: Context) {
    private val intent = Intent(context, getActivityClass())

    init {
      withAction(Action.VIEW)
    }

    fun withAddedIntentFlags(flags: Int): Builder {
      intent.addFlags(flags)
      return this
    }

    fun withIntentFlags(flags: Int): Builder {
      intent.flags = flags
      return this
    }

    fun withAction(action: Action?): Builder {
      intent.action = action?.let { getActionString(action) }
      return this
    }

    fun withEnableVideoIfAvailable(enableVideoIfAvailable: Boolean): Builder {
      intent.putExtra(getExtraString(Extra.ENABLE_VIDEO_IF_AVAILABLE), enableVideoIfAvailable)
      return this
    }

    fun withStartedFromFullScreen(startedFromFullScreen: Boolean): Builder {
      intent.putExtra(getExtraString(Extra.STARTED_FROM_FULLSCREEN), startedFromFullScreen)
      return this
    }

    fun withStartedFromCallLink(startedFromCallLink: Boolean): Builder {
      intent.putExtra(getExtraString(Extra.STARTED_FROM_CALL_LINK), startedFromCallLink)
      return this
    }

    fun withLaunchInPip(launchInPip: Boolean): Builder {
      intent.putExtra(getExtraString(Extra.LAUNCH_IN_PIP), launchInPip)
      return this
    }

    fun build(): Intent {
      return intent
    }
  }
}
