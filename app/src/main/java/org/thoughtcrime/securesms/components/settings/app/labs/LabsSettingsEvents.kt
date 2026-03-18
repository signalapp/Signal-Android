/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.labs

sealed interface LabsSettingsEvents {
  data class ToggleIndividualChatPlaintextExport(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleStoryArchive(val enabled: Boolean) : LabsSettingsEvents
  data class ToggleIncognito(val enabled: Boolean) : LabsSettingsEvents
}
