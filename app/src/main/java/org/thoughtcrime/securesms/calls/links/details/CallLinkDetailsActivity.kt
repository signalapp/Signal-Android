package org.thoughtcrime.securesms.calls.links.details

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FragmentWrapperActivity

class CallLinkDetailsActivity : FragmentWrapperActivity() {
  override fun getFragment(): Fragment = NavHostFragment.create(R.navigation.call_link_details)
}
