/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

/**
 * One-shot side effects that need an Activity or a FragmentManager, and therefore have to be carried out by the
 * fragment rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface ManageStorageSettingsAction {

  /** Open the media overview, where the user can see and delete what's taking up space. */
  data object NavigateToReviewStorage : ManageStorageSettingsAction

  /** Offer the paid backups tier, which optimized storage needs. */
  data object ShowUpgradeToEnableOptimizedStorage : ManageStorageSettingsAction
}
