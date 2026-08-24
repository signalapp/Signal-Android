package org.thoughtcrime.securesms.keyboard.emoji

import org.signal.emoji.EmojiEventListener
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment

interface EmojiKeyboardCallback :
  EmojiEventListener,
  EmojiKeyboardPageFragment.Callback,
  EmojiSearchFragment.Callback
