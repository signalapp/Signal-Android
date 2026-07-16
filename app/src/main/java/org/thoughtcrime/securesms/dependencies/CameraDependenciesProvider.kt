/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import org.signal.camera.CameraDependencies
import org.thoughtcrime.securesms.stories.Stories

object CameraDependenciesProvider : CameraDependencies.Provider {
  override fun isStoriesFeatureEnabled(): Boolean {
    return Stories.isFeatureEnabled()
  }
}
