package org.thoughtcrime.securesms.keyboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.util.ThemedFragment.themeResId
import org.thoughtcrime.securesms.util.ThemedFragment.themedInflate
import org.thoughtcrime.securesms.util.ThemedFragment.withTheme
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Hosts the emoji page of [MediaKeyboard], the only page its remaining hosts use.
 */
class EmojiKeyboardContainerFragment : Fragment() {

  private var emojiFragment: Fragment? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return themedInflate(R.layout.emoji_keyboard_container_fragment, inflater, container)
  }

  @Suppress("DEPRECATION")
  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    displayEmojiPage()
  }

  private fun displayEmojiPage() {
    val fragment = emojiFragment
    if (fragment != null) {
      (fragment as? KeyboardPageSelected)?.onPageSelected()
    } else {
      val newFragment = EmojiKeyboardPageFragment().withTheme(themeResId)
      childFragmentManager.beginTransaction()
        .add(R.id.fragment_container, newFragment)
        .commitAllowingStateLoss()
      emojiFragment = newFragment
    }

    findListener<MediaKeyboard.MediaKeyboardListener>()?.onKeyboardChanged(KeyboardPage.EMOJI)
  }

  /**
   * Called by [MediaKeyboard], which shows and hides this fragment in place rather than creating it fresh.
   */
  fun show() {
    findListener<MediaKeyboard.MediaKeyboardListener>()?.onShown()
    if (isAdded && view != null) {
      displayEmojiPage()
    }
  }

  fun hide() {
    findListener<MediaKeyboard.MediaKeyboardListener>()?.onHidden()
    if (isAdded && view != null) {
      emojiFragment?.let { childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss() }
      emojiFragment = null
    }
  }
}
