/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.emoji

import android.app.Application
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.emoji.EmojiEventListener
import org.signal.mediakeyboard.screens.emojikeyboard.EmojiKeyboardAction

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EmojiKeyboardViewTest {

  private val keyboardHeight = 800

  private val visibility = mutableListOf<String>()
  private val emoji = mutableListOf<String?>()
  private val keyEvents = mutableListOf<KeyEvent?>()

  private lateinit var view: EmojiKeyboardView

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    view = EmojiKeyboardView(context)
    FrameLayout(context).addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    view.visibilityListener = object : EmojiKeyboardView.VisibilityListener {
      override fun onShown() {
        visibility += "shown"
      }

      override fun onHidden() {
        visibility += "hidden"
      }
    }

    view.emojiEventListener = object : EmojiEventListener {
      override fun onEmojiSelected(emoji: String?) {
        this@EmojiKeyboardViewTest.emoji += emoji
      }

      override fun onKeyEvent(keyEvent: KeyEvent?) {
        keyEvents += keyEvent
      }
    }
  }

  @Test
  fun `starts hidden`() {
    assertThat(view.isShowing).isFalse()
  }

  @Test
  fun `showing takes the system keyboard's height`() {
    view.show(keyboardHeight, false)

    assertThat(view.isShowing).isTrue()
    assertThat(view.layoutParams.height).isEqualTo(keyboardHeight)
    assertThat(visibility).containsExactly("shown")
  }

  @Test
  fun `searching shrinks to fit above the system keyboard`() {
    view.show(keyboardHeight, false)

    view.onAction(EmojiKeyboardAction.SearchOpened)

    assertThat(view.isSearching).isTrue()
    assertThat(view.layoutParams.height).isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT)
  }

  @Test
  fun `closing search returns to the system keyboard's height`() {
    view.show(keyboardHeight, false)
    view.onAction(EmojiKeyboardAction.SearchOpened)

    view.onAction(EmojiKeyboardAction.SearchClosed)

    assertThat(view.isSearching).isFalse()
    assertThat(view.layoutParams.height).isEqualTo(keyboardHeight)
  }

  @Test
  fun `hiding while searching leaves search behind`() {
    view.show(keyboardHeight, false)
    view.onAction(EmojiKeyboardAction.SearchOpened)

    view.hide(true)

    assertThat(view.visibility).isEqualTo(View.GONE)
    assertThat(view.isSearching).isFalse()
    assertThat(view.layoutParams.height).isEqualTo(keyboardHeight)
    assertThat(visibility).containsExactly("shown", "hidden")
  }

  @Test
  fun `picking an emoji reports it`() {
    view.onAction(EmojiKeyboardAction.EmojiSelected("🎉"))

    assertThat(emoji).containsExactly("🎉")
  }

  @Test
  fun `backspace reports a delete key press`() {
    view.onAction(EmojiKeyboardAction.Backspace)

    assertThat(keyEvents.single()?.keyCode).isEqualTo(KeyEvent.KEYCODE_DEL)
    assertThat(keyEvents.single()?.action).isEqualTo(KeyEvent.ACTION_DOWN)
  }
}
