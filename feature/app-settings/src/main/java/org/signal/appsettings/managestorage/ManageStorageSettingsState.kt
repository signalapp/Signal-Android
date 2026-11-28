/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.managestorage

import org.signal.core.models.storage.KeepMessagesDuration

/**
 * Everything [ManageStorageSettingsScreen] needs to render. Every field has a default so `ManageStorageSettingsState()`
 * is a valid starting point for the view model, previews, and tests.
 */
data class ManageStorageSettingsState(
  /** How much storage the app is using, or null while it's still being computed. */
  val storageUsage: StorageUsage? = null,
  val onDeviceStorageOptimizationState: OnDeviceStorageOptimizationState = OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE,
  val keepMessagesDuration: KeepMessagesDuration = KeepMessagesDuration.FOREVER,
  /** How many messages each chat is trimmed to, or [ChatLengthLimitSettingsState.NO_LIMIT]. */
  val lengthLimit: Int = ChatLengthLimitSettingsState.NO_LIMIT,
  val syncTrimDeletes: Boolean = false,
  /** Only a primary device can push its chat limits out to linked devices. */
  val isPrimaryDevice: Boolean = true,
  /** Whether there are linked devices that deleting message history would reach too. */
  val isMultiDevice: Boolean = false,
  val dialog: Dialog = Dialog.None
) {

  val isOptimizeStorageVisible: Boolean
    get() = onDeviceStorageOptimizationState != OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE

  val isOptimizeStorageChecked: Boolean
    get() = onDeviceStorageOptimizationState == OnDeviceStorageOptimizationState.ENABLED

  /** Where the user stands with optimized storage, which offloads old media to their paid backup. */
  enum class OnDeviceStorageOptimizationState {
    /** The entire feature is not available and the option should not be displayed to the user. */
    FEATURE_NOT_AVAILABLE,

    /** The feature is available, but the user is not on the paid backups plan. */
    REQUIRES_PAID_TIER,

    /** The user is on the paid backups plan but optimized storage is disabled. */
    DISABLED,

    /** The user is on the paid backups plan and optimized storage is enabled. */
    ENABLED
  }

  /** Whichever dialog the screen is showing, if any. Only one is ever up at a time. */
  sealed interface Dialog {
    data object None : Dialog

    /** The first of two confirmations before every message is deleted. */
    data object ConfirmDeleteChatHistory : Dialog

    /** The second, final confirmation before every message is deleted. */
    data object ReconfirmDeleteChatHistory : Dialog

    /** Explains that optimized storage has to wait until the paid tier subscription finishes processing. */
    data object PaidTierPending : Dialog

    /** Warns that turning on optimized storage also removes old media from the on-device backup. */
    data object ConfirmOptimizeStorageWithLocalBackup : Dialog
  }
}
