package org.thoughtcrime.securesms.conversation.colors.ui.custom

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class CustomChatColorPagerAdapter(parentFragment: Fragment, private val arguments: Bundle) : FragmentStateAdapter(parentFragment) {
  override fun getItemCount(): Int = 2

  override fun createFragment(position: Int): Fragment {
    return when (position) {
      0 -> CustomChatColorCreatorPageFragment.forSingle(arguments)
      1 -> CustomChatColorCreatorPageFragment.forGradient(arguments)
      else -> {
        throw AssertionError()
      }
    }
  }
}
