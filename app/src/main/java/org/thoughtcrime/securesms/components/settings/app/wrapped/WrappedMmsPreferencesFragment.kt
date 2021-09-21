package org.thoughtcrime.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.preferences.MmsPreferencesFragment

class WrappedMmsPreferencesFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    toolbar.setTitle(R.string.preferences__advanced_mms_access_point_names)
    return MmsPreferencesFragment()
  }
}
