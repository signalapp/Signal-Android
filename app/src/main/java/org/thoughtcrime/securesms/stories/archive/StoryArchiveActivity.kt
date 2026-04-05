package org.thoughtcrime.securesms.stories.archive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity

class StoryArchiveActivity : PassphraseRequiredActivity() {

  companion object {
    fun createIntent(context: Context): Intent {
      return Intent(context, StoryArchiveActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    setContent {
      SignalTheme {
        StoryArchiveScreen(
          onNavigationClick = { onBackPressedDispatcher.onBackPressed() }
        )
      }
    }
  }
}
