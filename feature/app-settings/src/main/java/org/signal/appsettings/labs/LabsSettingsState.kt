/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.labs

/**
 * [LabsSettingsScreen] state: whether each internal-only pre-release feature is turned on.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class LabsSettingsState(
  val individualChatPlaintextExport: Boolean = false,
  val storyArchive: Boolean = false,
  val incognito: Boolean = false,
  val betterSearch: Boolean = false,
  val starredMessages: Boolean = false,
  val stickerReplies: Boolean = false,
  val improvedMessageDeletion: Boolean = false
)
