package org.thoughtcrime.securesms.stories.settings

import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLSettingsActivity

class StorySettingsActivity : DSLSettingsActivity() {
  companion object {
    fun getIntent(context: Context): Intent {
      return Intent(context, StorySettingsActivity::class.java)
        .putExtra(ARG_NAV_GRAPH, R.navigation.story_privacy_settings)
        .putExtra(ARG_START_BUNDLE, bundleOf("title_id" to R.string.StoriesPrivacySettingsFragment__story_privacy))
    }
  }
}
