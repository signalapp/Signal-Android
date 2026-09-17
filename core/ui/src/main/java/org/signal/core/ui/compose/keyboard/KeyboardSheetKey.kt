/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.keyboard

/**
 * Identifies a keyboard a [KeyboardSheetScaffold] can put up. Callers declare their own, so a screen
 * only knows about the keyboards it offers.
 */
@JvmInline
value class KeyboardSheetKey(val name: String)
