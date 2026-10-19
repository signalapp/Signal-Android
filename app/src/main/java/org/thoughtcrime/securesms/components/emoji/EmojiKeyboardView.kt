/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.signal.core.ui.compose.navigationBarsCompat
import org.signal.core.ui.compose.safeDrawingCompat
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.emoji.EmojiEventListener
import org.signal.mediakeyboard.screens.emojikeyboard.EmojiKeyboard
import org.signal.mediakeyboard.screens.emojikeyboard.EmojiKeyboardAction
import org.thoughtcrime.securesms.components.InputAwareLayout
import org.thoughtcrime.securesms.mediakeyboard.SignalEmojiKeyboardRepository

/**
 * [EmojiKeyboard] as an [InputAwareLayout.InputView]. The host should leave it up when the system keyboard appears
 * while [isSearching].
 */
class EmojiKeyboardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs), InputAwareLayout.InputView {

  companion object {
    private val DELETE_KEY_EVENT = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
  }

  /** Follows the keyboard coming and going, for a toggle that switches its icon to match. */
  interface VisibilityListener {
    fun onShown()
    fun onHidden()
  }

  /** Receives the emoji picked, and backspace as a [KeyEvent.KEYCODE_DEL] key event. */
  var emojiEventListener: EmojiEventListener? = null

  var visibilityListener: VisibilityListener? = null

  /** Sends picked emoji and backspace to [editText]. */
  fun attachTo(editText: EmojiEditText) {
    emojiEventListener = object : EmojiEventListener {
      override fun onEmojiSelected(emoji: String?) = editText.insertEmoji(emoji)

      override fun onKeyEvent(keyEvent: KeyEvent?) {
        editText.dispatchKeyEvent(keyEvent)
      }
    }
  }

  /** Whether search has taken the keyboard's place, and so wants the system keyboard alongside it. */
  var isSearching by mutableStateOf(false)
    private set

  private var keyboardShown by mutableStateOf(false)
  private var keyboardHeight = 0
  private var navigationBarInset by mutableIntStateOf(0)
  private var composeView: ComposeView? = null

  private val repository by lazy {
    val applicationContext = context.applicationContext
    SignalEmojiKeyboardRepository(applicationContext, RecentEmojiPageModel(applicationContext, RecentEmojiPageModel.RECENT_STORAGE_KEY))
  }

  init {
    visibility = GONE
  }

  override fun show(height: Int, immediate: Boolean) {
    keyboardHeight = height
    ensureContent()
    updateHeight()
    visibility = VISIBLE
    keyboardShown = true
    visibilityListener?.onShown()
  }

  override fun hide(immediate: Boolean) {
    visibility = GONE
    keyboardShown = false
    isSearching = false
    navigationBarInset = 0
    updateHeight()
    visibilityListener?.onHidden()
  }

  override fun isShowing(): Boolean = visibility == VISIBLE

  override fun claimNavigationBar(inset: Int): Boolean {
    if (navigationBarInset != inset) {
      navigationBarInset = inset
      updateHeight()
    }
    return true
  }

  @VisibleForTesting
  internal fun onAction(action: EmojiKeyboardAction) {
    when (action) {
      is EmojiKeyboardAction.EmojiSelected -> emojiEventListener?.onEmojiSelected(action.emoji)
      EmojiKeyboardAction.Backspace -> emojiEventListener?.onKeyEvent(DELETE_KEY_EVENT)
      EmojiKeyboardAction.SearchOpened -> {
        isSearching = true
        updateHeight()
      }
      EmojiKeyboardAction.SearchClosed -> {
        isSearching = false
        updateHeight()
        ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
      }
    }
  }

  private fun updateHeight() {
    val params = layoutParams ?: return
    params.height = if (isSearching) ViewGroup.LayoutParams.WRAP_CONTENT else keyboardHeight + navigationBarInset
    layoutParams = params
  }

  private fun ensureContent() {
    if (composeView != null) {
      return
    }

    composeView = ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        SignalTheme {
          // The screen around this keeps clear of the system bars and keyboard, except for a navigation bar it claimed,
          // which the keyboard's rail paints behind.
          val insetsKeptClear = if (navigationBarInset > 0) {
            WindowInsets.safeDrawingCompat.exclude(WindowInsets.navigationBarsCompat.only(WindowInsetsSides.Bottom))
          } else {
            WindowInsets.safeDrawingCompat
          }

          Surface(
            color = SignalTheme.colors.colorSurface1,
            modifier = Modifier.consumeWindowInsets(insetsKeptClear)
          ) {
            EmojiKeyboard(
              repository = repository,
              isSearching = isSearching,
              isShown = keyboardShown,
              onAction = ::onAction
            )
          }
        }
      }
    }

    addView(composeView)
  }
}
