package org.thoughtcrime.securesms.components.settings.app.wrapped

import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.preferences.StoragePreferenceFragment

class WrappedStoragePreferenceFragment : SettingsWrapperFragment() {
  override fun getFragment(): Fragment {
    return StoragePreferenceFragment()
  }
}
