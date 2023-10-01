/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.ConversationActivity

/**
 * Flavor of [ConversationActivity] used for quick replies to notifications in pre-API 24 devices.
 */
class ConversationPopupActivity : ConversationActivity() {
  override fun onPreCreate() {
    super.onPreCreate()
    overridePendingTransition(R.anim.slide_from_top, R.anim.slide_to_top)
  }

  @Suppress("DEPRECATION")
  override fun onCreate(bundle: Bundle?, ready: Boolean) {
    window.setFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND, WindowManager.LayoutParams.FLAG_DIM_BEHIND)

    window.attributes = window.attributes.apply {
      alpha = 1.0f
      dimAmount = 0.1f
      gravity = Gravity.TOP
    }

    val display = windowManager.defaultDisplay
    val width = display.width
    val height = display.height

    if (height > width) {
      window.setLayout((width * .85).toInt(), (height * .5).toInt())
    } else {
      window.setLayout((width * .7).toInt(), (height * .75).toInt())
    }

    super.onCreate(bundle, ready)
  }

  override fun onPause() {
    super.onPause()
    if (isFinishing) {
      overridePendingTransition(R.anim.slide_from_top, R.anim.slide_to_top)
    }
  }
}
