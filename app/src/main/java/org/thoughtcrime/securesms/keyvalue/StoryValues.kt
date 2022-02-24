package org.thoughtcrime.securesms.keyvalue

internal class StoryValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    /*
     * User option to completely disable stories
     */
    private const val MANUAL_FEATURE_DISABLE = "stories.disable"

    private const val LAST_FONT_VERSION_CHECK = "stories.last.font.version.check"

    /**
     * Used to check whether we should display certain dialogs.
     */
    private const val USER_HAS_ADDED_TO_A_STORY = "user.has.added.to.a.story"
  }

  override fun onFirstEverAppLaunch() = Unit

  override fun getKeysToIncludeInBackup(): MutableList<String> = mutableListOf(MANUAL_FEATURE_DISABLE, USER_HAS_ADDED_TO_A_STORY)

  var isFeatureDisabled: Boolean by booleanValue(MANUAL_FEATURE_DISABLE, false)

  var lastFontVersionCheck: Long by longValue(LAST_FONT_VERSION_CHECK, 0)

  var userHasAddedToAStory: Boolean by booleanValue(USER_HAS_ADDED_TO_A_STORY, false)
}
