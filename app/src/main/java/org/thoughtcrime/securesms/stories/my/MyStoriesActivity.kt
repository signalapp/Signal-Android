package org.thoughtcrime.securesms.stories.my

import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.components.FragmentWrapperActivity

class MyStoriesActivity : FragmentWrapperActivity() {
  override fun getFragment(): Fragment {
    return MyStoriesFragment()
  }
}
