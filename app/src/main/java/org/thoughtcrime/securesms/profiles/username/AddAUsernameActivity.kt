package org.thoughtcrime.securesms.profiles.username

import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.profiles.manage.UsernameEditFragmentArgs
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

class AddAUsernameActivity : BaseActivity() {
  private val dynamicTheme: DynamicTheme = DynamicNoActionBarTheme()
  private val contentViewId: Int = R.layout.fragment_container

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(contentViewId)
    dynamicTheme.onCreate(this)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          NavHostFragment.create(
            R.navigation.create_username,
            UsernameEditFragmentArgs.Builder().setIsInRegistration(true).build().toBundle()
          )
        )
        .commit()
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }
}
