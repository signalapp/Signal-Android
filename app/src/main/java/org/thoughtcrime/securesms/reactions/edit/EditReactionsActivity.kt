package org.thoughtcrime.securesms.reactions.edit

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import org.thoughtcrime.securesms.util.DynamicTheme

class EditReactionsActivity : PassphraseRequiredActivity() {

  private val theme: DynamicTheme = DynamicNoActionBarTheme()

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    if (intent.extras?.getBoolean(ARG_FORCE_DARK_MODE) == true) {
      delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    }
    theme.onCreate(this)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(android.R.id.content, EditReactionsFragment())
        .commit()
    }
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  companion object {
    const val ARG_FORCE_DARK_MODE = "arg_dark"
  }
}
