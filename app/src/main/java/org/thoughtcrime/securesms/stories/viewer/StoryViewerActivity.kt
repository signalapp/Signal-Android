package org.thoughtcrime.securesms.stories.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.RecipientId

class StoryViewerActivity : PassphraseRequiredActivity() {

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.fragment_container)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(R.id.fragment_container, StoryViewerFragment.create(intent.getParcelableExtra(ARG_START_RECIPIENT_ID)!!))
        .commit()
    }
  }

  companion object {
    private const val ARG_START_RECIPIENT_ID = "start.recipient.id"

    fun createIntent(context: Context, storyId: RecipientId): Intent {
      return Intent(context, StoryViewerActivity::class.java)
        .putExtra(ARG_START_RECIPIENT_ID, storyId)
    }
  }
}
