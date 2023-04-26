package org.thoughtcrime.securesms.conversation.v2

import android.content.Intent
import androidx.fragment.app.Fragment
import org.thoughtcrime.securesms.components.FragmentWrapperActivity
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

/**
 * Wrapper activity for ConversationFragment.
 */
class ConversationActivity : FragmentWrapperActivity(), VoiceNoteMediaControllerOwner {

  private val theme = DynamicNoActionBarTheme()
  override val voiceNoteMediaController = VoiceNoteMediaController(this, true)

  override fun onPreCreate() {
    theme.onCreate(this)
  }

  override fun onResume() {
    super.onResume()
    theme.onResume(this)
  }

  override fun getFragment(): Fragment = ConversationFragment().apply {
    arguments = intent.extras
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    error("ON NEW INTENT")
  }
}
