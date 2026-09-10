/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.AndroidFragment
import org.signal.core.ui.util.ThemeUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.compose.mediakeyboard.MediaKeyboardController
import org.thoughtcrime.securesms.components.compose.mediakeyboard.MediaKeyboardEvents
import org.thoughtcrime.securesms.components.compose.mediakeyboard.MediaKeyboardHeight
import org.thoughtcrime.securesms.components.compose.mediakeyboard.MediaKeyboardScaffold
import org.thoughtcrime.securesms.conversation.v2.keyboard.AttachmentKeyboardFragment
import org.thoughtcrime.securesms.keyboard.KeyboardPagerFragment
import kotlin.math.roundToInt

/** A bubble's keyboard takes a little over half the window, as it does for the older hosts. */
private const val BUBBLE_HEIGHT_FRACTION = 0.55f

/**
 * Displays a chat screen for a given conversation.
 *
 * @param controller The MediaKeyboardController to control the media keyboard
 * @param onEvent The MediaKeyboard events stream for interacting with the media keyboard
 * @param scrim The color information for the top and bottom scrim
 * @param isBubble Whether we're displaying content in a bubble
 * @param backgroundView The chat wallpaper
 * @param contentView The area that actually moves up when the keyboards appear
 * @param overlayView The long press overlay, which a keyboard neither covers nor resizes
 */
@Composable
fun ChatScreen(
  controller: MediaKeyboardController,
  onEvent: (MediaKeyboardEvents) -> Unit,
  scrims: ChatScrimState,
  isBubble: Boolean,
  backgroundView: View,
  contentView: View,
  overlayView: View,
  modifier: Modifier = Modifier
) {
  val minimumHeight = dimensionResource(R.dimen.default_custom_keyboard_size)
  val topMargin = dimensionResource(R.dimen.min_custom_keyboard_top_margin_portrait)
  val mediaKeyboardColor = Color(ThemeUtil.getThemedColor(LocalContext.current, R.attr.mediaKeyboardBottomBarBackgroundColor))
  val attachmentKeyboardColor = scrims.attachmentKeyboardColor

  val keyboardHeight = remember(minimumHeight, topMargin, isBubble) {
    MediaKeyboardHeight(
      minimum = minimumHeight,
      topMargin = topMargin,
      overrideForWindow = if (isBubble) {
        { windowHeightPx -> (windowHeightPx * BUBBLE_HEIGHT_FRACTION).roundToInt() }
      } else {
        null
      }
    )
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      // A bubble's host has already accounted for the system bars, but not for the keyboard, so
      // that one inset has to survive or nothing lifts the input off the system keyboard.
      .then(if (isBubble) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.exclude(WindowInsets.ime)) else Modifier)
  ) {
    AndroidView(
      factory = { backgroundView },
      modifier = Modifier.fillMaxSize()
    )

    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .windowInsetsTopHeight(WindowInsets.statusBars)
        .background(Color(scrims.statusBarColor))
    )

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .windowInsetsBottomHeight(WindowInsets.navigationBars)
        .background(Color(scrims.navigationBarColor))
    )

    MediaKeyboardScaffold(
      controller = controller,
      onEvent = onEvent,
      keyboardsProvider = {
        keyboard(
          key = ChatKeyboards.Media,
          containerColor = mediaKeyboardColor
        ) {
          AndroidFragment(
            clazz = KeyboardPagerFragment::class.java,
            modifier = Modifier.fillMaxSize()
          )
        }

        keyboard(
          key = ChatKeyboards.Attachment,
          containerColor = attachmentKeyboardColor
        ) {
          AndroidFragment(
            clazz = AttachmentKeyboardFragment::class.java,
            modifier = Modifier.fillMaxSize()
          )
        }
      },
      keyboardHeight = keyboardHeight
    ) {
      AndroidView(
        factory = { contentView },
        modifier = Modifier.fillMaxSize()
      )
    }

    // Above the scaffold so a closing keyboard neither covers nor resizes it. The bottom inset is
    // left on; the overlay subtracts the navigation bar itself.
    AndroidView(
      factory = { overlayView },
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBars.add(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)))
    )
  }
}
