package org.thoughtcrime.securesms.calls.new

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.components.FragmentWrapperActivity

class NewCallActivity : FragmentWrapperActivity() {
  @SuppressLint("DiscouragedApi")
  override fun getFragment(): Fragment = NewCallFragment()
}
