/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.emoji

import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.rules.ExternalResource
import org.signal.core.util.PartAuthorityUris

/**
 * Initializes [EmojiDependencies] with the Robolectric application and a relaxed provider, so that
 * tests can exercise the engine without an app module behind it.
 */
class MockEmojiDependenciesRule : ExternalResource() {
  override fun before() {
    PartAuthorityUris.init("org.signal.emoji.test")
    EmojiDependencies.init(ApplicationProvider.getApplicationContext(), mockk(relaxed = true))
  }
}
