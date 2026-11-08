/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.my

/**
 * One-shot side effects that need the fragment manager or the nav graph, and therefore have to be carried out
 * by the host rather than the screen itself.
 *
 * Actions are logged, so be sure `toString()` contains nothing sensitive.
 */
sealed interface MyStorySettingsAction {

  /** Leave the screen. */
  data object NavigateBack : MyStorySettingsAction

  /** Show everyone counted as a Signal connection. */
  data object ShowAllSignalConnections : MyStorySettingsAction

  /** Open the picker for who to hide the story from. */
  data object NavigateToAllExcept : MyStorySettingsAction

  /** Open the picker for who to share the story with. */
  data object NavigateToOnlyShareWith : MyStorySettingsAction

  /** Explain what a Signal connection is. */
  data object ShowSignalConnectionsEducation : MyStorySettingsAction
}
