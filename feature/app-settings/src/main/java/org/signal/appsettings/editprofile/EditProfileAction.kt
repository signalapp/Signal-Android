/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

/**
 * One-shot side effects the host carries out for [EditProfileScreen].
 */
sealed interface EditProfileAction {

  /** Show the user's avatar full screen. */
  data object OpenAvatarPreview : EditProfileAction

  /** Leave the profile for the screen where the user can start a monthly donation. */
  data object OpenSubscriptions : EditProfileAction

  /** Open the app store, or the download page for builds that don't come from one. */
  data object OpenAppUpdate : EditProfileAction

  /** Start registration over for a device that's no longer registered. */
  data object StartReregistration : EditProfileAction
}
