package org.signal.storage.keyvalue

class OnboardingValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val SHOW_NEW_GROUP = "onboarding.new_group"
    private const val SHOW_INVITE_FRIENDS = "onboarding.invite_friends"
    private const val SHOW_APPEARANCE = "onboarding.appearance"
    private const val SHOW_ADD_PHOTO = "onboarding.add_photo"
    private const val SHOW_SET_UP_USERNAME = "onboarding.set_up_username"
  }

  public override fun onFirstEverAppLaunch() {
    putBoolean(SHOW_NEW_GROUP, true)
    putBoolean(SHOW_INVITE_FRIENDS, true)
    putBoolean(SHOW_APPEARANCE, true)
    putBoolean(SHOW_ADD_PHOTO, true)
    putBoolean(SHOW_SET_UP_USERNAME, true)
  }

  override val keysToIncludeInBackup: List<String>
    get() = emptyList()

  fun clearAll() {
    setShowNewGroup(false)
    setShowInviteFriends(false)
    setShowAppearance(false)
    setShowAddPhoto(false)
    setShowSetUpUsername(false)
  }

  fun hasOnboarding(): Boolean {
    return shouldShowNewGroup() ||
      shouldShowInviteFriends() ||
      shouldShowAppearance() ||
      shouldShowAddPhoto() ||
      shouldShowSetUpUsername()
  }

  fun setShowNewGroup(value: Boolean) {
    putBoolean(SHOW_NEW_GROUP, value)
  }

  fun shouldShowNewGroup(): Boolean {
    return getBoolean(SHOW_NEW_GROUP, false)
  }

  fun setShowInviteFriends(value: Boolean) {
    putBoolean(SHOW_INVITE_FRIENDS, value)
  }

  fun shouldShowInviteFriends(): Boolean {
    return getBoolean(SHOW_INVITE_FRIENDS, false)
  }

  fun setShowAppearance(value: Boolean) {
    putBoolean(SHOW_APPEARANCE, value)
  }

  fun shouldShowAppearance(): Boolean {
    return getBoolean(SHOW_APPEARANCE, false)
  }

  fun setShowAddPhoto(value: Boolean) {
    putBoolean(SHOW_ADD_PHOTO, value)
  }

  fun shouldShowAddPhoto(): Boolean {
    return getBoolean(SHOW_ADD_PHOTO, false)
  }

  fun setShowSetUpUsername(value: Boolean) {
    putBoolean(SHOW_SET_UP_USERNAME, value)
  }

  fun shouldShowSetUpUsername(): Boolean {
    return getBoolean(SHOW_SET_UP_USERNAME, false)
  }
}
