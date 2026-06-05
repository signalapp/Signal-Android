/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Allows sharing the AnimatedVisibilityScope in deep hierarchies. Recommended by Android docs.
 */
val LocalAnimateVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
