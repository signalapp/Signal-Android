package org.thoughtcrime.securesms.stories.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.StoryTextPostModel

class StoryViewerActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    supportPostponeEnterTransition()

    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.fragment_container)

    voiceNoteMediaController = VoiceNoteMediaController(this)

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(
          R.id.fragment_container,
          StoryViewerFragment.create(
            intent.getParcelableExtra(ARG_START_RECIPIENT_ID)!!,
            intent.getLongExtra(ARG_START_STORY_ID, -1L),
            intent.getBooleanExtra(ARG_HIDDEN_STORIES, false),
            intent.getParcelableExtra(ARG_CROSSFADE_TEXT_MODEL),
            intent.getParcelableExtra(ARG_CROSSFADE_IMAGE_URI),
            intent.getStringExtra(ARG_CROSSFADE_IMAGE_BLUR),
            intent.getParcelableArrayListExtra(ARG_RECIPIENT_IDS)!!
          )
        )
        .commit()
    }
  }

  override fun onEnterAnimationComplete() {
    if (Build.VERSION.SDK_INT >= 21) {
      window.transitionBackgroundFadeDuration = 100
    }
  }

  companion object {
    private const val ARG_START_RECIPIENT_ID = "start.recipient.id"
    private const val ARG_START_STORY_ID = "start.story.id"
    private const val ARG_HIDDEN_STORIES = "hidden_stories"
    private const val ARG_CROSSFADE_TEXT_MODEL = "crossfade.text.model"
    private const val ARG_CROSSFADE_IMAGE_URI = "crossfade.image.uri"
    private const val ARG_CROSSFADE_IMAGE_BLUR = "crossfade.image.blur"
    private const val ARG_RECIPIENT_IDS = "recipient_ids"

    @JvmStatic
    fun createIntent(
      context: Context,
      recipientId: RecipientId,
      storyId: Long = -1L,
      onlyIncludeHiddenStories: Boolean = false,
      storyThumbTextModel: StoryTextPostModel? = null,
      storyThumbUri: Uri? = null,
      storyThumbBlur: BlurHash? = null,
      recipientIds: List<RecipientId> = emptyList()
    ): Intent {
      return Intent(context, StoryViewerActivity::class.java)
        .putExtra(ARG_START_RECIPIENT_ID, recipientId)
        .putExtra(ARG_START_STORY_ID, storyId)
        .putExtra(ARG_HIDDEN_STORIES, onlyIncludeHiddenStories)
        .putExtra(ARG_CROSSFADE_TEXT_MODEL, storyThumbTextModel)
        .putExtra(ARG_CROSSFADE_IMAGE_URI, storyThumbUri)
        .putExtra(ARG_CROSSFADE_IMAGE_BLUR, storyThumbBlur?.hash)
        .putParcelableArrayListExtra(ARG_RECIPIENT_IDS, ArrayList(recipientIds))
    }
  }
}
