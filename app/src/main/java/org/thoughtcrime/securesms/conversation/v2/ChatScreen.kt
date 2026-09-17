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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.compose.AndroidFragment
import org.signal.core.ui.compose.keyboard.KeyboardSheetAction
import org.signal.core.ui.compose.keyboard.KeyboardSheetController
import org.signal.core.ui.compose.keyboard.KeyboardSheetHeight
import org.signal.core.ui.compose.keyboard.KeyboardSheetScaffold
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.safeDrawingCompat
import org.signal.core.ui.compose.statusBarsCompat
import org.signal.core.ui.util.ThemeUtil
import org.signal.mediakeyboard.MediaKeyboard
import org.signal.mediakeyboard.MediaKeyboardAction
import org.signal.mediakeyboard.MediaKeyboardTab
import org.signal.mediakeyboard.data.MediaKeyboardRepository
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.keyboard.AttachmentKeyboardFragment
import kotlin.math.roundToInt

/** A bubble's keyboard takes a little over half the window, as it does for the older hosts. */
private const val BUBBLE_HEIGHT_FRACTION = 0.55f

/**
 * Displays a chat screen for a given conversation.
 *
 * @param controller The KeyboardSheetController to control the media keyboard
 * @param onScaffoldAction Receives what the scaffold itself reports: which keyboard is up, and
 *   what the system keyboard is doing
 * @param mediaKeyboardRepository Supplies the media keyboard's emoji, sticker, and gif data
 * @param onMediaKeyboardAction Receives what the user picks from the media keyboard, and anything
 *   only the host can carry out
 * @param mediaKeyboardTabs The only media keyboard tabs to offer, or null for all of them
 * @param mediaKeyboardInitialTab The media keyboard tab to open on, from the mode remembered across
 *   runs, or null to open on the first one offered
 * @param minimumVisibleContentPx How much of [contentView] the expanded media keyboard must leave
 *   in view: message rows, plus the toolbar and input panel that sit either side of them
 * @param scrim The color information for the top and bottom scrim
 * @param isBubble Whether we're displaying content in a bubble
 * @param conversationView The conversation's own view hierarchy, and the only interop view here.
 *   A second one sharing pointer input would leave this one without its ACTION_HOVER_EXIT, which is
 *   why the long press overlay is Compose. See stylus-hover-interop.md.
 */
@Composable
fun ChatScreen(
  controller: KeyboardSheetController,
  onScaffoldAction: (KeyboardSheetAction) -> Unit,
  mediaKeyboardRepository: MediaKeyboardRepository,
  onMediaKeyboardAction: (MediaKeyboardAction) -> Unit,
  mediaKeyboardTabs: Set<MediaKeyboardTab>?,
  mediaKeyboardInitialTab: MediaKeyboardTab?,
  minimumVisibleContentPx: Int,
  scrims: ChatScrimState,
  isBubble: Boolean,
  conversationView: View,
  overlayController: ChatReactionOverlayController,
  modifier: Modifier = Modifier
) {
  val minimumHeight = dimensionResource(R.dimen.default_custom_keyboard_size)
  val topMargin = dimensionResource(R.dimen.min_custom_keyboard_top_margin_portrait)
  val mediaKeyboardColor = Color(ThemeUtil.getThemedColor(LocalContext.current, R.attr.mediaKeyboardBottomBarBackgroundColor))
  val attachmentKeyboardColor = scrims.attachmentKeyboardColor

  val minimumVisibleContent = with(LocalDensity.current) { minimumVisibleContentPx.toDp() }

  val keyboardHeight = remember(minimumHeight, topMargin, minimumVisibleContent, isBubble) {
    KeyboardSheetHeight(
      minimum = minimumHeight,
      topMargin = topMargin,
      minimumContentVisible = minimumVisibleContent,
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
      .then(if (isBubble) Modifier.consumeWindowInsets(WindowInsets.safeDrawingCompat.exclude(WindowInsets.ime)) else Modifier)
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .windowInsetsTopHeight(WindowInsets.statusBarsCompat)
        .background(Color(scrims.statusBarColor))
    )

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .windowInsetsBottomHeight(WindowInsets.navigationBarsCompat)
        .background(Color(scrims.navigationBarColor))
    )

    KeyboardSheetScaffold(
      controller = controller,
      onAction = onScaffoldAction,
      keyboardsProvider = {
        keyboard(
          key = ChatKeyboards.Media,
          containerColor = mediaKeyboardColor,
          expandable = true
        ) {
          MediaKeyboard(
            repository = mediaKeyboardRepository,
            onAction = onMediaKeyboardAction,
            tabs = mediaKeyboardTabs,
            initialTab = mediaKeyboardInitialTab
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
        factory = { conversationView },
        modifier = Modifier.fillMaxSize()
      )
    }

    // Above the scaffold so a closing keyboard neither covers nor resizes it. The bottom inset is
    // left on; the placement subtracts the navigation bar itself.
    ChatReactionOverlay(
      controller = overlayController,
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.statusBarsCompat.add(WindowInsets.safeDrawingCompat.only(WindowInsetsSides.Horizontal)))
    )
  }
}
